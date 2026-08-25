/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;

import org.ta4j.core.analysis.montecarlo.MonteCarloContext;
import org.ta4j.core.analysis.montecarlo.MonteCarloMethod;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Simulation engine shared by all Monte Carlo forecast indicators.
 *
 * <p>
 * Owns everything that is technique-independent: stability gating, historical
 * window assembly, deterministic seed derivation, terminal value mapping, and
 * forecast assembly. The swappable {@link MonteCarloMethod} receives a prepared
 * {@link MonteCarloContext} and generates the terminal samples.
 *
 * <p>
 * The engine also selects the forecast RNG stream: by default the historical
 * shared {@link SplittableRandom} stream is handed to the method, while RNG
 * version {@code 1} provides independent per-path streams so that forecasts are
 * reproducible regardless of path execution order -- the mode required for
 * accelerated and native-parity evaluation.
 */
final class MonteCarloSimulation {

    /**
     * Selects the forecast RNG stream. Version {@code 0} (default) restores the
     * historical shared stream per decision index; version {@code 1} selects the
     * deterministic per-path stream used for native parity and acceleration.
     */
    static final String RNG_VERSION_PROPERTY = "ta4j.forecast.rngVersion";

    private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
    private final ReturnIndicator returnIndicator;
    private final MonteCarloSettings settings;
    private final MonteCarloMethod method;

    MonteCarloSimulation(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator,
            MonteCarloSettings settings, MonteCarloMethod method) {
        this.stateIndicator = validateStateIndicator(stateIndicator);
        this.returnIndicator = this.stateIndicator.getReturnIndicator();
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        IndicatorUtils.requireSameSeries(returnIndicator, this.stateIndicator);
    }

    Forecast project(int index, TerminalValueMapper mapper) {
        if (index < getCountOfUnstableBars()) {
            return Forecast.unstable(index, settings.horizon());
        }
        ReturnMomentState rawState = stateIndicator.getValue(index);
        if (rawState == null) {
            return Forecast.unstable(index, settings.horizon());
        }
        ReturnMoments moments = rawState.moments();
        if (moments == null || moments.index() != index || !moments.isStable()
                || moments.representation() != ReturnRepresentation.LOG) {
            return Forecast.unstable(index, settings.horizon());
        }
        NumFactory numFactory = returnIndicator.getBarSeries().numFactory();
        List<Num> historicalReturns = historicalReturns(index, numFactory);
        if (historicalReturns.size() != settings.lookbackBarCount()) {
            return Forecast.unstable(index, settings.horizon());
        }

        RandomGenerator random = new SplittableRandom(mixSeed(settings.seed(), index, settings.horizon()));
        IntFunction<RandomGenerator> perPathRandoms = legacyStreamRequested() ? null
                : path -> DeterministicRandom.forPath(settings.seed(), index, settings.horizon(), path);
        List<Num> terminalSamples = method.terminalReturns(new MonteCarloContext(index, settings.horizon(),
                settings.iterationCount(), historicalReturns, moments, random, numFactory, perPathRandoms));
        if (terminalSamples == null || terminalSamples.size() != settings.iterationCount()) {
            return Forecast.unstable(index, settings.horizon());
        }
        List<Num> terminalValues = new ArrayList<>(terminalSamples.size());
        for (Num cumulativeReturn : terminalSamples) {
            if (!Num.isFinite(cumulativeReturn)) {
                return Forecast.unstable(index, settings.horizon());
            }
            if (cumulativeReturn.getNumFactory() != numFactory) {
                cumulativeReturn = numFactory.numOf(cumulativeReturn.getDelegate());
            }
            Num terminalValue;
            try {
                terminalValue = mapper.map(cumulativeReturn);
            } catch (ArithmeticException exception) {
                return Forecast.unstable(index, settings.horizon());
            }
            if (!Num.isFinite(terminalValue)) {
                return Forecast.unstable(index, settings.horizon());
            }
            terminalValues.add(terminalValue);
        }
        return Forecast.ofSamples(index, settings.horizon(), terminalValues, settings.quantileProbabilities());
    }

    int getCountOfUnstableBars() {
        return Math.max(stateIndicator.getCountOfUnstableBars(),
                returnIndicator.getCountOfUnstableBars() + settings.lookbackBarCount() - 1);
    }

    int getHorizon() {
        return settings.horizon();
    }

    private static boolean legacyStreamRequested() {
        String configured = System.getProperty(RNG_VERSION_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return switch (configured.trim()) {
        case "0" -> true;
        case "1" -> false;
        default -> throw new IllegalArgumentException(
                RNG_VERSION_PROPERTY + " must be '0' or '1', but was '" + configured + "'");
        };
    }

    /**
     * Whether the explicit per-path stream was selected via
     * {@code -Dta4j.forecast.rngVersion=1}. Accelerated evaluation may only run
     * when this mode is active because it relies on path-order-independent
     * reproducibility.
     */
    static boolean isPerPathRngSelected() {
        return !legacyStreamRequested();
    }

    private List<Num> historicalReturns(int index, NumFactory numFactory) {
        List<Num> historicalReturns = new ArrayList<>(settings.lookbackBarCount());
        for (int barIndex = index - settings.lookbackBarCount() + 1; barIndex <= index; barIndex++) {
            Num value = normalize(returnIndicator.getValue(barIndex), numFactory);
            if (value == null) {
                return List.of();
            }
            historicalReturns.add(value);
        }
        return historicalReturns;
    }

    private static ReturnForecastStateIndicator<? extends ReturnMomentState> validateStateIndicator(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        ReturnForecastStateIndicator<? extends ReturnMomentState> validated = Objects.requireNonNull(stateIndicator,
                "stateIndicator must not be null");
        ReturnIndicator source = Objects.requireNonNull(validated.getReturnIndicator(),
                "stateIndicator returnIndicator must not be null");
        if (source.getReturnRepresentation() != ReturnRepresentation.LOG
                || validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("stateIndicator must use ReturnRepresentation.LOG");
        }
        return validated;
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        if (!Num.isFinite(value)) {
            return null;
        }
        Num normalized = numFactory.numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized : null;
    }

    private static long mixSeed(long seed, int index, int horizon) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L + ((long) index << 32) + index;
        value = Long.rotateLeft(value, 27) * 0x3C79AC492BA7B653L;
        value ^= 0x1C69B3F74AC4AE35L + horizon;
        value = Long.rotateLeft(value, 31) * 0x1C69B3F74AC4AE35L;
        return value ^ value >>> 33;
    }

    @FunctionalInterface
    interface TerminalValueMapper {
        Num map(Num cumulativeReturn);
    }

    /**
     * Counter-based deterministic random generator whose stream for one simulated
     * path depends only on the seed derivation inputs, never on execution order.
     */
    static final class DeterministicRandom implements RandomGenerator {

        private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

        private static final double DOUBLE_UNIT = 0x1.0p-53;

        private long state;

        private DeterministicRandom(long state) {
            this.state = state;
        }

        static DeterministicRandom forPath(long seed, int decisionIndex, int horizon, int pathIndex) {
            if (decisionIndex < 0) {
                throw new IllegalArgumentException("decisionIndex must be >= 0");
            }
            if (horizon < 1) {
                throw new IllegalArgumentException("horizon must be >= 1");
            }
            if (pathIndex < 0) {
                throw new IllegalArgumentException("pathIndex must be >= 0");
            }
            long value = seed;
            value = mix64(value ^ (Integer.toUnsignedLong(decisionIndex) * 0xD1B54A32D192ED03L));
            value = mix64(value ^ (Integer.toUnsignedLong(horizon) * 0x94D049BB133111EBL));
            value = mix64(value ^ (Integer.toUnsignedLong(pathIndex) * 0xDB4F0B9175AE2165L));
            return new DeterministicRandom(value);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be > 0");
            }
            long candidate = nextLong() >>> 1;
            long remainder = candidate % bound;
            while (candidate - remainder + bound - 1 < 0L) {
                candidate = nextLong() >>> 1;
                remainder = candidate % bound;
            }
            return (int) remainder;
        }

        @Override
        public double nextGaussian() {
            double radius = StrictMath.sqrt(-2d * StrictMath.log(1d - nextDouble()));
            return radius * StrictMath.cos(2d * StrictMath.PI * nextDouble());
        }

        @Override
        public int nextInt() {
            return (int) nextLong();
        }

        @Override
        public long nextLong() {
            state += GOLDEN_GAMMA;
            return mix64(state);
        }

        @Override
        public double nextDouble() {
            return (nextLong() >>> 11) * DOUBLE_UNIT;
        }

        @Override
        public float nextFloat() {
            return (float) nextDouble();
        }

        @Override
        public boolean nextBoolean() {
            return nextLong() < 0L;
        }

        private static long mix64(long value) {
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }
    }
}

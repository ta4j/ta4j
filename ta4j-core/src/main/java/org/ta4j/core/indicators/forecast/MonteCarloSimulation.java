/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class MonteCarloSimulation {

    private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
    private final ReturnIndicator returnIndicator;
    private final MonteCarloSettings settings;

    MonteCarloSimulation(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator,
            MonteCarloSettings settings) {
        this.stateIndicator = validateStateIndicator(stateIndicator);
        this.returnIndicator = this.stateIndicator.getReturnIndicator();
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        IndicatorUtils.requireSameSeries(returnIndicator, this.stateIndicator);
    }

    Forecast project(int index, TerminalValueMapper mapper) {
        if (index < getCountOfUnstableBars()) {
            return Forecast.unstable(index, settings.horizon());
        }
        ReturnMomentState rawState = stateIndicator.getValue(index);
        if (rawState == null || rawState.index() != index || !rawState.isStable()
                || rawState.representation() != ReturnRepresentation.LOG) {
            return Forecast.unstable(index, settings.horizon());
        }
        NumFactory numFactory = returnIndicator.getBarSeries().numFactory();
        ProjectionState state = ProjectionState.from(rawState.moments(), numFactory);
        if (state == null) {
            return Forecast.unstable(index, settings.horizon());
        }
        List<Num> historicalReturns = historicalReturns(index, numFactory);
        if (historicalReturns.size() != settings.lookbackBarCount()) {
            return Forecast.unstable(index, settings.horizon());
        }

        ShockSampler sampler = ShockSampler.create(settings.shockModel(), historicalReturns, state, numFactory);
        RandomGenerator random = new SplittableRandom(mixSeed(settings.seed(), index, settings.horizon()));
        List<Num> terminalValues = new ArrayList<>(settings.iterationCount());
        for (int iteration = 0; iteration < settings.iterationCount(); iteration++) {
            Num cumulativeReturn = simulatePath(random, sampler, state, numFactory);
            if (!Num.isFinite(cumulativeReturn)) {
                return Forecast.unstable(index, settings.horizon());
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

    private List<Num> historicalReturns(int index, NumFactory numFactory) {
        int startIndex = index - settings.lookbackBarCount() + 1;
        List<Num> values = new ArrayList<>(settings.lookbackBarCount());
        for (int i = startIndex; i <= index; i++) {
            Num value = returnIndicator.getValue(i);
            if (!Num.isFinite(value)) {
                return List.of();
            }
            Num normalized = normalize(value, numFactory);
            if (!Num.isFinite(normalized)) {
                return List.of();
            }
            values.add(normalized);
        }
        return values;
    }

    private Num simulatePath(RandomGenerator random, ShockSampler sampler, ProjectionState startingState,
            NumFactory numFactory) {
        Num cumulativeReturn = numFactory.zero();
        Num drift = startingState.drift();
        Num mean = startingState.mean();
        Num variance = startingState.variance();
        Num volatility = startingState.volatility();
        for (int step = 0; step < settings.horizon(); step++) {
            Num shock = sampler.sample(random);
            Num stepReturn = settings
                    .shockModel() == MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP ? shock
                            : drift.plus(volatility.multipliedBy(shock));
            cumulativeReturn = cumulativeReturn.plus(stepReturn);
            if (settings.volatilityUpdateMode() == MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA) {
                Num decay = numFactory.numOf(settings.volatilityDecayFactor());
                Num oneMinusDecay = numFactory.one().minus(decay);
                Num deviation = stepReturn.minus(mean);
                mean = mean.multipliedBy(decay).plus(stepReturn.multipliedBy(oneMinusDecay));
                variance = variance.multipliedBy(decay)
                        .plus(deviation.multipliedBy(deviation).multipliedBy(oneMinusDecay));
                volatility = variance.isZero() ? numFactory.zero() : variance.sqrt();
            }
        }
        return cumulativeReturn;
    }

    private static ReturnForecastStateIndicator<? extends ReturnMomentState> validateStateIndicator(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        ReturnForecastStateIndicator<? extends ReturnMomentState> validated = Objects.requireNonNull(stateIndicator,
                "stateIndicator must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
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

    private record ProjectionState(Num mean, Num drift, Num variance, Num volatility) {

        private static ProjectionState from(ReturnMoments moments, NumFactory numFactory) {
            if (moments == null || !moments.isStable() || moments.observationCount() <= 0) {
                return null;
            }
            Num mean = normalize(moments.mean(), numFactory);
            Num drift = normalize(moments.drift(), numFactory);
            Num variance = normalize(moments.variance(), numFactory);
            if (!Num.isFinite(mean) || !Num.isFinite(drift) || !Num.isFinite(variance) || variance.isNegative()) {
                return null;
            }
            Num volatility = variance.isZero() ? numFactory.zero() : variance.sqrt();
            return Num.isFinite(volatility) ? new ProjectionState(mean, drift, variance, volatility) : null;
        }
    }

    @FunctionalInterface
    private interface ShockSampler {
        Num sample(RandomGenerator random);

        static ShockSampler create(MonteCarloReturnProjectionIndicator.ShockModel model, List<Num> historicalReturns,
                ProjectionState state, NumFactory numFactory) {
            return switch (model) {
            case HISTORICAL_BOOTSTRAP -> historicalBootstrap(historicalReturns);
            case STANDARDIZED_EMPIRICAL -> standardizedEmpirical(historicalReturns, state, numFactory);
            case NORMAL -> random -> numFactory.numOf(random.nextGaussian());
            };
        }

        private static ShockSampler historicalBootstrap(List<Num> historicalReturns) {
            List<Num> samples = List.copyOf(historicalReturns);
            return random -> samples.get(random.nextInt(samples.size()));
        }

        private static ShockSampler standardizedEmpirical(List<Num> historicalReturns, ProjectionState state,
                NumFactory numFactory) {
            if (state.volatility().isZero()) {
                return random -> numFactory.zero();
            }
            List<Num> shocks = historicalReturns.stream()
                    .map(value -> value.minus(state.mean()).dividedBy(state.volatility()))
                    .toList();
            return random -> shocks.get(random.nextInt(shocks.size()));
        }
    }
}

record MonteCarloSettings(int horizon, int iterationCount, int lookbackBarCount, long seed,
        MonteCarloReturnProjectionIndicator.ShockModel shockModel,
        MonteCarloReturnProjectionIndicator.VolatilityUpdateMode volatilityUpdateMode, double volatilityDecayFactor,
        List<Double> quantileProbabilities) {

    MonteCarloSettings {
        if (horizon < 1 || iterationCount < 1 || lookbackBarCount < 1) {
            throw new IllegalArgumentException("horizon, iterationCount, and lookbackBarCount must be >= 1");
        }
        shockModel = Objects.requireNonNull(shockModel, "shockModel must not be null");
        volatilityUpdateMode = Objects.requireNonNull(volatilityUpdateMode, "volatilityUpdateMode must not be null");
        if (Double.isNaN(volatilityDecayFactor) || volatilityDecayFactor <= 0d || volatilityDecayFactor >= 1d) {
            throw new IllegalArgumentException("volatilityDecayFactor must be in (0, 1)");
        }
        List<Double> input = Objects.requireNonNull(quantileProbabilities, "quantileProbabilities must not be null");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("quantileProbabilities must not be empty");
        }
        TreeSet<Double> sorted = new TreeSet<>();
        for (Double probability : input) {
            Double value = Objects.requireNonNull(probability, "quantile probability must not be null");
            if (Double.isNaN(value) || value < 0d || value > 1d) {
                throw new IllegalArgumentException("quantile probability must be in [0, 1]");
            }
            sorted.add(value);
        }
        quantileProbabilities = List.copyOf(sorted);
    }
}

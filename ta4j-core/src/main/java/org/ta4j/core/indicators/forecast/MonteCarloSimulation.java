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
import org.ta4j.core.indicators.forecast.method.MonteCarloContext;
import org.ta4j.core.indicators.forecast.method.MonteCarloMethod;
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
 */
final class MonteCarloSimulation {

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
        List<Num> terminalSamples = method.terminalReturns(new MonteCarloContext(index, settings.horizon(),
                settings.iterationCount(), historicalReturns, moments, random, numFactory));
        if (terminalSamples == null || terminalSamples.size() != settings.iterationCount()) {
            return Forecast.unstable(index, settings.horizon());
        }
        List<Num> terminalValues = new ArrayList<>(terminalSamples.size());
        for (Num cumulativeReturn : terminalSamples) {
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
}

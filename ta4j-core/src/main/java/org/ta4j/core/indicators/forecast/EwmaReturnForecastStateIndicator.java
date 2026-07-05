/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Recursive EWMA state estimator for log-return forecasts.
 *
 * @since 0.22.9
 */
public class EwmaReturnForecastStateIndicator extends RecursiveCachedIndicator<ReturnForecastState> {

    private final Indicator<Num> returnIndicator;
    private final EwmaReturnForecastStateConfig config;

    /**
     * Constructor.
     *
     * @param returnIndicator log-return source
     * @param config          state configuration
     * @since 0.22.9
     */
    public EwmaReturnForecastStateIndicator(Indicator<Num> returnIndicator, EwmaReturnForecastStateConfig config) {
        super(Objects.requireNonNull(returnIndicator, "returnIndicator must not be null"));
        this.returnIndicator = returnIndicator;
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    protected ReturnForecastState calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return ReturnForecastState.unstable(index);
        }
        ReturnForecastState previous = index > 0 ? getValue(index - 1) : ReturnForecastState.unstable(index);
        Num currentReturn = returnIndicator.getValue(index);
        if (ForecastNumerics.isInvalid(currentReturn)) {
            return ReturnForecastState.unstable(index);
        }
        if (!previous.isStable()) {
            return initialize(index);
        }
        return update(index, previous, currentReturn);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return returnIndicator.getCountOfUnstableBars() + config.initializationBarCount() - 1;
    }

    private ReturnForecastState initialize(int index) {
        int startIndex = index - config.initializationBarCount() + 1;
        List<Num> returns = new ArrayList<>(config.initializationBarCount());
        for (int i = startIndex; i <= index; i++) {
            Num value = returnIndicator.getValue(i);
            if (ForecastNumerics.isInvalid(value)) {
                return ReturnForecastState.unstable(index);
            }
            returns.add(value);
        }
        NumFactory numFactory = getBarSeries().numFactory();
        Num mean = mean(numFactory, returns);
        Num variance = variance(numFactory, returns, mean);
        Num volatility = variance.sqrt();
        Num drift = config.driftMode() == DriftMode.ZERO ? numFactory.zero() : mean;
        return new ReturnForecastState(index, returns.size(), true, mean, drift, variance, volatility);
    }

    private ReturnForecastState update(int index, ReturnForecastState previous, Num currentReturn) {
        NumFactory numFactory = getBarSeries().numFactory();
        Num decay = numFactory.numOf(config.decayFactor());
        Num oneMinusDecay = numFactory.one().minus(decay);
        Num mean = previous.mean().multipliedBy(decay).plus(currentReturn.multipliedBy(oneMinusDecay));
        Num deviation = currentReturn.minus(previous.mean());
        Num variance = previous.variance()
                .multipliedBy(decay)
                .plus(deviation.multipliedBy(deviation).multipliedBy(oneMinusDecay));
        Num volatility = variance.sqrt();
        Num drift = config.driftMode() == DriftMode.ZERO ? numFactory.zero() : mean;
        return new ReturnForecastState(index, previous.observationCount() + 1, true, mean, drift, variance, volatility);
    }

    private static Num mean(NumFactory numFactory, List<Num> values) {
        Num sum = numFactory.zero();
        for (Num value : values) {
            sum = sum.plus(value);
        }
        return sum.dividedBy(numFactory.numOf(values.size()));
    }

    private static Num variance(NumFactory numFactory, List<Num> values, Num mean) {
        Num sum = numFactory.zero();
        for (Num value : values) {
            Num deviation = value.minus(mean);
            sum = sum.plus(deviation.multipliedBy(deviation));
        }
        return sum.dividedBy(numFactory.numOf(values.size()));
    }
}

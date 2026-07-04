/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Adapts a forecast distribution indicator into a point forecast indicator.
 *
 * @since 0.22.9
 */
public class ForwardForecastIndicator extends CachedIndicator<Num> {

    private final ForecastDistributionIndicator<Num> forecastIndicator;
    private final ForecastReducer reducer;

    /**
     * Constructor.
     *
     * @param forecastIndicator source forecast distribution indicator
     * @param reducer           reducer used to derive one point value
     * @since 0.22.9
     */
    public ForwardForecastIndicator(ForecastDistributionIndicator<Num> forecastIndicator, ForecastReducer reducer) {
        super(Objects.requireNonNull(forecastIndicator, "forecastIndicator must not be null"));
        this.forecastIndicator = forecastIndicator;
        this.reducer = Objects.requireNonNull(reducer, "reducer must not be null");
    }

    @Override
    protected Num calculate(int index) {
        ForecastDistribution<Num> distribution = forecastIndicator.getValue(index);
        if (distribution == null || !distribution.defined()) {
            return NaN.NaN;
        }
        Num value = reducer.reduce(distribution);
        if (ForecastNumerics.isInvalid(value)) {
            return NaN.NaN;
        }
        return value;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return forecastIndicator.getCountOfUnstableBars();
    }
}

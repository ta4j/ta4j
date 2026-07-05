/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Adapts a forecast distribution indicator into a point forecast indicator.
 *
 * @since 0.22.9
 */
public class ForwardForecastIndicator extends CachedIndicator<Num> {

    private final ForecastDistributionIndicator forecastIndicator;
    private final Function<ForecastDistribution<Num>, Num> valueResolver;

    /**
     * Constructor.
     *
     * @param forecastIndicator source forecast distribution indicator
     * @param valueResolver     resolver used to derive one point value from a
     *                          stable distribution
     * @since 0.22.9
     */
    public ForwardForecastIndicator(ForecastDistributionIndicator forecastIndicator,
            Function<ForecastDistribution<Num>, Num> valueResolver) {
        super(Objects.requireNonNull(forecastIndicator, "forecastIndicator must not be null"));
        this.forecastIndicator = forecastIndicator;
        this.valueResolver = Objects.requireNonNull(valueResolver, "valueResolver must not be null");
    }

    @Override
    protected Num calculate(int index) {
        ForecastDistribution<Num> distribution = forecastIndicator.getValue(index);
        if (distribution == null || !distribution.isStable()) {
            return NaN.NaN;
        }
        Num value = valueResolver.apply(distribution);
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

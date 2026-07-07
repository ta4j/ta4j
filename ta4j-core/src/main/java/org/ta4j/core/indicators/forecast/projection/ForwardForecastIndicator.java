/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Adapts a forecast summary indicator into a point forecast indicator.
 *
 * @since 0.22.9
 */
public class ForwardForecastIndicator extends CachedIndicator<Num> {

    private final ForecastProjectionIndicator forecastIndicator;
    private final Function<Forecast<Num>, Num> valueResolver;

    /**
     * Constructor.
     *
     * @param forecastIndicator source forecast projection indicator
     * @param valueResolver     resolver used to derive one point value from a
     *                          stable forecast
     * @since 0.22.9
     */
    public ForwardForecastIndicator(ForecastProjectionIndicator forecastIndicator,
            Function<Forecast<Num>, Num> valueResolver) {
        super(Objects.requireNonNull(forecastIndicator, "forecastIndicator must not be null"));
        this.forecastIndicator = forecastIndicator;
        this.valueResolver = Objects.requireNonNull(valueResolver, "valueResolver must not be null");
    }

    @Override
    protected Num calculate(int index) {
        Forecast<Num> forecast = forecastIndicator.getValue(index);
        if (forecast == null || !forecast.isStable()) {
            return NaN.NaN;
        }
        Num value = valueResolver.apply(forecast);
        if (IndicatorUtils.isInvalid(value)) {
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

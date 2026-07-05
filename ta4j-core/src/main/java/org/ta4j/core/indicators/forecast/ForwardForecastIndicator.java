/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.PredictionSnapshot;

/**
 * Adapts a forecast distribution indicator into a point forecast indicator.
 *
 * @since 0.22.9
 */
public class ForwardForecastIndicator extends CachedIndicator<Num> {

    private final ForecastPredictionIndicator forecastIndicator;
    private final Function<PredictionSnapshot.Forecast<Num>, Num> valueResolver;

    /**
     * Constructor.
     *
     * @param forecastIndicator source forecast prediction indicator
     * @param valueResolver     resolver used to derive one point value from a
     *                          stable distribution
     * @since 0.22.9
     */
    public ForwardForecastIndicator(ForecastPredictionIndicator forecastIndicator,
            Function<PredictionSnapshot.Forecast<Num>, Num> valueResolver) {
        super(Objects.requireNonNull(forecastIndicator, "forecastIndicator must not be null"));
        this.forecastIndicator = forecastIndicator;
        this.valueResolver = Objects.requireNonNull(valueResolver, "valueResolver must not be null");
    }

    @Override
    protected Num calculate(int index) {
        PredictionSnapshot.Forecast<Num> forecast = forecastIndicator.getValue(index);
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

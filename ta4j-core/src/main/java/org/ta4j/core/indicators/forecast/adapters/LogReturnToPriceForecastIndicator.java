/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.adapters;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.forecast.projection.Forecast;

/**
 * Converts cumulative log-return forecast distributions to price forecasts.
 *
 * @since 0.22.9
 */
public class LogReturnToPriceForecastIndicator extends CachedIndicator<Forecast<Num>>
        implements ForecastProjectionIndicator {

    private final Indicator<Num> priceIndicator;
    private final ReturnForecastProjectionIndicator logReturnForecastProjection;

    /**
     * Constructor using an explicit cumulative log-return forecast.
     *
     * @param priceIndicator              source price indicator read at the
     *                                    decision index
     * @param logReturnForecastProjection cumulative log-return projection source
     * @since 0.22.9
     */
    public LogReturnToPriceForecastIndicator(Indicator<Num> priceIndicator,
            ReturnForecastProjectionIndicator logReturnForecastProjection) {
        super(IndicatorUtils.requireSameSeries(
                Objects.requireNonNull(priceIndicator, "priceIndicator must not be null"),
                validateLogReturnProjection(logReturnForecastProjection)));
        this.priceIndicator = priceIndicator;
        this.logReturnForecastProjection = logReturnForecastProjection;
    }

    @Override
    protected Forecast<Num> calculate(int index) {
        Forecast<Num> logReturnForecast = logReturnForecastProjection.getValue(index);
        if (logReturnForecast == null || !logReturnForecast.isStable()) {
            int horizon = logReturnForecast == null ? 1 : logReturnForecast.horizon();
            return Forecast.unstable(index, horizon);
        }
        Num price = priceIndicator.getValue(index);
        if (!Num.isFinite(price) || !price.isPositive()) {
            return Forecast.unstable(index, logReturnForecast.horizon());
        }
        return logReturnForecast.map(cumulativeLogReturn -> {
            if (!Num.isFinite(cumulativeLogReturn)) {
                return NaN.NaN;
            }
            return price.multipliedBy(cumulativeLogReturn.exp());
        });
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(priceIndicator.getCountOfUnstableBars(), logReturnForecastProjection.getCountOfUnstableBars());
    }

    private static ReturnForecastProjectionIndicator validateLogReturnProjection(
            ReturnForecastProjectionIndicator projection) {
        ReturnForecastProjectionIndicator validated = Objects.requireNonNull(projection,
                "logReturnForecastProjection must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("logReturnForecastProjection must use ReturnRepresentation.LOG");
        }
        return validated;
    }
}

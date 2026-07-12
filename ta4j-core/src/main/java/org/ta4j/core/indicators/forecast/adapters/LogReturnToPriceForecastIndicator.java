/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.adapters;

import java.util.LinkedHashMap;
import java.util.Map;
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
 * Returns an unstable forecast when any exponential price conversion overflows
 * or otherwise produces a non-finite value.
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
        Num mean = toPrice(price, logReturnForecast.mean());
        Num median = toPrice(price, logReturnForecast.median());
        Num standardDeviation = toPrice(price, logReturnForecast.standardDeviation());
        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (Map.Entry<Double, Num> entry : logReturnForecast.quantiles().entrySet()) {
            quantiles.put(entry.getKey(), toPrice(price, entry.getValue()));
        }
        if (!Num.isFinite(mean) || !Num.isFinite(median) || !Num.isFinite(standardDeviation)
                || quantiles.values().stream().anyMatch(value -> !Num.isFinite(value))) {
            return Forecast.unstable(index, logReturnForecast.horizon());
        }
        return Forecast.ofSummary(index, logReturnForecast.horizon(), logReturnForecast.sampleCount(), mean, median,
                standardDeviation, quantiles);
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

    private static Num toPrice(Num price, Num cumulativeLogReturn) {
        if (!Num.isFinite(cumulativeLogReturn)) {
            return NaN.NaN;
        }
        return price.multipliedBy(cumulativeLogReturn.exp());
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

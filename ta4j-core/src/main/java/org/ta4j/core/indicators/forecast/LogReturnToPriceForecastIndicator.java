/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.PredictionSnapshot;

/**
 * Converts cumulative log-return forecast distributions to price forecasts.
 *
 * @since 0.22.9
 */
public class LogReturnToPriceForecastIndicator extends CachedIndicator<PredictionSnapshot.Forecast<Num>>
        implements ForecastPredictionIndicator {

    private final Indicator<Num> priceIndicator;
    private final ForecastPredictionIndicator logReturnForecastIndicator;

    /**
     * Constructor.
     *
     * @param priceIndicator             source price indicator read at the decision
     *                                   index
     * @param logReturnForecastIndicator cumulative log-return forecast source
     * @since 0.22.9
     */
    public LogReturnToPriceForecastIndicator(Indicator<Num> priceIndicator,
            ForecastPredictionIndicator logReturnForecastIndicator) {
        super(IndicatorUtils.requireSameSeries(
                Objects.requireNonNull(priceIndicator, "priceIndicator must not be null"),
                Objects.requireNonNull(logReturnForecastIndicator, "logReturnForecastIndicator must not be null")));
        this.priceIndicator = priceIndicator;
        this.logReturnForecastIndicator = logReturnForecastIndicator;
    }

    @Override
    protected PredictionSnapshot.Forecast<Num> calculate(int index) {
        PredictionSnapshot.Forecast<Num> logReturnForecast = logReturnForecastIndicator.getValue(index);
        if (logReturnForecast == null || !logReturnForecast.isStable()) {
            int horizon = logReturnForecast == null ? 1 : logReturnForecast.horizon();
            return PredictionSnapshot.Forecast.unstable(index, horizon);
        }
        Num price = priceIndicator.getValue(index);
        if (IndicatorUtils.isInvalid(price) || !price.isPositive()) {
            return PredictionSnapshot.Forecast.unstable(index, logReturnForecast.horizon());
        }
        return logReturnForecast.map(cumulativeLogReturn -> {
            if (IndicatorUtils.isInvalid(cumulativeLogReturn)) {
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
        return Math.max(priceIndicator.getCountOfUnstableBars(), logReturnForecastIndicator.getCountOfUnstableBars());
    }
}

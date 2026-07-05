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

/**
 * Converts cumulative log-return forecast distributions to price forecasts.
 *
 * @since 0.22.9
 */
public class LogReturnToPriceForecastIndicator extends CachedIndicator<ForecastDistribution<Num>>
        implements ForecastDistributionIndicator {

    private final Indicator<Num> priceIndicator;
    private final ForecastDistributionIndicator logReturnForecastIndicator;

    /**
     * Constructor.
     *
     * @param priceIndicator             source price indicator read at the decision
     *                                   index
     * @param logReturnForecastIndicator cumulative log-return forecast source
     * @since 0.22.9
     */
    public LogReturnToPriceForecastIndicator(Indicator<Num> priceIndicator,
            ForecastDistributionIndicator logReturnForecastIndicator) {
        super(IndicatorUtils.requireSameSeries(
                Objects.requireNonNull(priceIndicator, "priceIndicator must not be null"),
                Objects.requireNonNull(logReturnForecastIndicator, "logReturnForecastIndicator must not be null")));
        this.priceIndicator = priceIndicator;
        this.logReturnForecastIndicator = logReturnForecastIndicator;
    }

    @Override
    protected ForecastDistribution<Num> calculate(int index) {
        ForecastDistribution<Num> logReturnForecast = logReturnForecastIndicator.getValue(index);
        if (logReturnForecast == null || !logReturnForecast.isStable()) {
            int horizon = logReturnForecast == null ? 1 : logReturnForecast.horizon();
            return ForecastDistribution.unstable(index, horizon);
        }
        Num price = priceIndicator.getValue(index);
        if (ForecastNumerics.isInvalid(price) || !price.isPositive()) {
            return ForecastDistribution.unstable(index, logReturnForecast.horizon());
        }
        return logReturnForecast.map(cumulativeLogReturn -> {
            if (ForecastNumerics.isInvalid(cumulativeLogReturn)) {
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

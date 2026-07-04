/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Convenience factories for forecast indicator pipelines.
 *
 * @since 0.22.9
 */
public final class ForecastIndicators {

    private ForecastIndicators() {
    }

    /**
     * Creates a close-price forecast pipeline backed by log returns, EWMA state,
     * and Monte Carlo simulation.
     *
     * @param series         bar series
     * @param stateConfig    EWMA state configuration
     * @param forecastConfig Monte Carlo forecast configuration
     * @return close-price forecast distribution indicator
     * @since 0.22.9
     */
    public static ForecastDistributionIndicator<Num> ewmaVolatilityClosePriceForecast(BarSeries series,
            EwmaReturnForecastStateConfig stateConfig, MonteCarloForecastConfig forecastConfig) {
        return ewmaVolatilityClosePriceForecast(new ClosePriceIndicator(series), stateConfig, forecastConfig);
    }

    /**
     * Creates a price forecast pipeline backed by log returns, EWMA state, and
     * Monte Carlo simulation.
     *
     * @param priceIndicator source price indicator
     * @param stateConfig    EWMA state configuration
     * @param forecastConfig Monte Carlo forecast configuration
     * @return price forecast distribution indicator
     * @since 0.22.9
     */
    public static ForecastDistributionIndicator<Num> ewmaVolatilityClosePriceForecast(Indicator<Num> priceIndicator,
            EwmaReturnForecastStateConfig stateConfig, MonteCarloForecastConfig forecastConfig) {
        Indicator<Num> price = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        EwmaReturnForecastStateConfig validatedStateConfig = Objects.requireNonNull(stateConfig,
                "stateConfig must not be null");
        MonteCarloForecastConfig validatedForecastConfig = Objects.requireNonNull(forecastConfig,
                "forecastConfig must not be null");
        LogReturnIndicator returns = new LogReturnIndicator(price);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, validatedStateConfig);
        MonteCarloReturnForecastIndicator returnForecast = new MonteCarloReturnForecastIndicator(returns, state,
                validatedForecastConfig);
        return new LogReturnToPriceForecastIndicator(price, returnForecast);
    }

    /**
     * Creates a median close-price point forecast from the standard EWMA volatility
     * pipeline.
     *
     * @param priceIndicator source price indicator
     * @param stateConfig    EWMA state configuration
     * @param forecastConfig Monte Carlo forecast configuration
     * @return median price point forecast
     * @since 0.22.9
     */
    public static Indicator<Num> ewmaVolatilityMedianClosePriceForecast(Indicator<Num> priceIndicator,
            EwmaReturnForecastStateConfig stateConfig, MonteCarloForecastConfig forecastConfig) {
        return new ForwardForecastIndicator(
                ewmaVolatilityClosePriceForecast(priceIndicator, stateConfig, forecastConfig),
                ForecastReducers.median());
    }
}

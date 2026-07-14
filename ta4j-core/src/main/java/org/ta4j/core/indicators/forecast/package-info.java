/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Forecast indicators and prediction adapters.
 * <p>
 * Forecast values are made at a decision index and must only depend on source
 * data available at or before that index. Evaluation code may compare those
 * forecasts with later realized bars, but forecast indicators in this package
 * must not read future source values while producing {@code getValue(i)}.
 * <p>
 * The root package contains the primary forecast indicators users normally
 * instantiate: hidden-state estimators such as
 * {@link org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator},
 * projection indicators such as
 * {@link org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator},
 * and the constructor-first price forecast facade
 * {@link org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator}.
 * Framework contracts, state records, point-projection adapters, and explicit
 * analytic conversion bridges live in the {@code state}, {@code projection},
 * and {@code adapters} subpackages. Monte Carlo price forecasts transform every
 * terminal path before summarizing it; summary-only conversion is explicitly
 * labeled as an analytic approximation.
 *
 * @since 0.22.9
 */
package org.ta4j.core.indicators.forecast;

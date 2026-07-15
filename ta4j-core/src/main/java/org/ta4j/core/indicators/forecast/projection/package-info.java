/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Forecast projection contracts, summary values, and point-value adapters.
 * <p>
 * Projection indicators turn a hidden state into a forward {@link Forecast}
 * summary. Point adapters expose one summary field, such as median or a
 * quantile, as a normal {@code Indicator<Num>} for rule and indicator
 * composition. {@link ForecastSupport} distinguishes unavailable, empirical,
 * and analytic distributions. The compatibility {@link Forecast#sampleCount()}
 * accessor reports empirical support only; estimator training and calibration
 * observations remain model metadata.
 *
 * @since 0.22.9
 */
package org.ta4j.core.indicators.forecast.projection;

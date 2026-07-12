/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Hidden forecast state contracts and data records.
 * <p>
 * State indicators estimate latent inputs, such as rolling return mean and
 * volatility, that projection indicators can consume without coupling to a
 * specific estimator implementation. {@link ForecastState} provides the common
 * return-domain summary, while concrete records may add estimator-specific
 * fields. {@link ForecastFeatureExtractor} is the explicit primitive boundary
 * for distance and regression models.
 *
 * @since 0.22.9
 */
package org.ta4j.core.indicators.forecast.state;

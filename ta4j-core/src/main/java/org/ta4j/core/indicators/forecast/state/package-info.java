/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Hidden forecast state contracts and data records.
 * <p>
 * State indicators estimate latent inputs, such as rolling return mean and
 * volatility, that projection indicators can consume without coupling to a
 * specific estimator implementation. {@link ForecastState} provides only index
 * and stability lifecycle; {@link ReturnMomentState} composes validated,
 * representation-aware {@link ReturnMoments} for return models. Return-derived
 * estimators implement {@link ReturnForecastStateIndicator}. Feature extractors
 * publish a {@link ForecastFeatureSchema} before crossing the primitive
 * boundary used by distance and regression models.
 * {@link RoughVolatilityForecastState} demonstrates the rich-state composition
 * pattern: common moments remain reusable while model-specific roughness,
 * vol-of-vol, and horizon variance fields stay typed and defensively immutable.
 * {@link OnlineChangePointForecastState} applies the same composition pattern
 * to recent-change probability and immutable, typed {@link RunLengthPosterior}
 * summaries without exposing the filter's internal arrays.
 *
 * @since 0.22.9
 */
package org.ta4j.core.indicators.forecast.state;

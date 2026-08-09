/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Statistical indicators for dispersion, smoothing, normalization, correlation,
 * and dependence analysis.
 *
 * <p>
 * Use this package when you need volatility-aware or distribution-aware signal
 * shaping, rolling relationship analysis, or regime-conditioned comparisons.
 * Common entry points include
 * {@link org.ta4j.core.indicators.statistics.StandardDeviationIndicator},
 * {@link org.ta4j.core.indicators.statistics.HurstExponentIndicator}, and
 * {@link org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator}.
 * Correlation-oriented entry points include
 * {@link org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator},
 * {@link org.ta4j.core.indicators.statistics.SpearmanRankCorrelationIndicator},
 * {@link org.ta4j.core.indicators.statistics.KendallTauIndicator},
 * {@link org.ta4j.core.indicators.statistics.LaggedCorrelationIndicator},
 * {@link org.ta4j.core.indicators.statistics.DistanceCorrelationIndicator},
 * {@link org.ta4j.core.indicators.statistics.MutualInformationIndicator}, and
 * {@link org.ta4j.core.indicators.statistics.RegimeSegmentedCorrelationIndicator}.
 * {@link org.ta4j.core.indicators.statistics.LeadLagCorrelationAnalyzer} scans
 * a lag range into a full correlation profile, and
 * {@link org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator}
 * measures shape similarity under time-axis distortion.
 * Sparse near-coincident Boolean event streams are scored with
 * {@link org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator},
 * a rolling F1 scorer over deterministic one-to-one event matching; the
 * event-dependence machinery lives in the
 * {@link org.ta4j.core.indicators.statistics.event} subpackage.
 * </p>
 */
package org.ta4j.core.indicators.statistics;

/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Deterministic event-stream mutual-information evaluation.
 *
 * <p>
 * {@link org.ta4j.core.analysis.event.EventMutualInformationEvaluator}
 * quantifies how much a continuous predictor reduces uncertainty about whether
 * an event occurs in an explicit future bar window, reporting raw and
 * normalized mutual information with prevalence and bin diagnostics.
 * {@link org.ta4j.core.analysis.event.EventMutualInformationConfig} controls
 * binning, history policy, and the forward target window; the history policy is
 * the shared
 * {@link org.ta4j.core.analysis.AnalysisContext.MissingHistoryPolicy}.
 *
 * <p>
 * The package is intended for offline research such as scoring momentum
 * zero-cross events against causal swing-confirmation events; it is not a
 * trading-record performance criterion. Event-stream synchronization and F1
 * scoring live in
 * {@link org.ta4j.core.indicators.statistics.EventSynchronizationIndicator}.
 *
 * @see org.ta4j.core.analysis.event.EventMutualInformationEvaluator
 * @since 0.24.2
 */
package org.ta4j.core.analysis.event;

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

/**
 * Selection policy for the deterministic best-lag choice of
 * {@link LeadLagCorrelationAnalyzer}.
 *
 * <p>
 * The signed policy picks the lag with the highest Pearson correlation, so a
 * strongly negative relationship loses to a mildly positive one. The absolute
 * policy picks the lag with the largest correlation magnitude, which is the
 * right choice when a strong negative relationship is as interesting as a
 * strong positive one.
 * </p>
 *
 * @since 0.24.1
 */
public enum LagSelectionPolicy {

    /**
     * Select lags by the highest signed correlation.
     */
    MAXIMUM_CORRELATION,
    /**
     * Select lags by the highest absolute correlation.
     */
    MAXIMUM_ABSOLUTE_CORRELATION
}

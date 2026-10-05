/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.frequency;

/**
 * Supported return sampling granularities.
 *
 * <p>
 * Bar and time-based values can be resolved from a
 * {@link org.ta4j.core.BarSeries} alone. {@link #TRADE} requires an API that
 * also receives a {@link org.ta4j.core.TradingRecord}.
 *
 * @since 0.22.2
 */
public enum SamplingFrequency {
    BAR, SECOND, MINUTE, HOUR, DAY, WEEK, MONTH,

    /**
     * Samples one return per included position interval.
     *
     * <p>
     * Use this with criteria that can inspect positions, such as
     * {@link org.ta4j.core.criteria.SharpeRatioCriterion} and
     * {@link org.ta4j.core.criteria.SortinoRatioCriterion}. Helpers that only group
     * bar indices, such as {@link SamplingFrequencyIndexes}, reject this value
     * because they cannot see the trading record.
     *
     * @since 0.23.1
     */
    TRADE
}

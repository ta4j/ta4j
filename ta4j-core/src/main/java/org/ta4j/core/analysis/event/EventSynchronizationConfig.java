/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Objects;

/**
 * Immutable configuration for {@link EventSynchronizationEvaluator}.
 *
 * <p>
 * Signed lag convention: for a matched predicted event {@code p} and reference
 * event {@code r}, {@code offset = r - p}. A positive offset means the
 * prediction leads the reference by {@code offset} bars, zero means exact
 * coincidence, and a negative offset means the prediction lags the reference. A
 * pair is eligible when {@code -maxLagBars <= offset <= maxLeadBars}.
 *
 * @param maxLeadBars      maximum bars a prediction may lead its reference
 *                         ({@code >= 0})
 * @param maxLagBars       maximum bars a prediction may lag its reference
 *                         ({@code >= 0})
 * @param historyPolicy    how to treat requested history outside the series
 * @param emptyEventPolicy what precision/recall/F1 mean when both streams have
 *                         no events
 * @since 0.24.2
 */
public record EventSynchronizationConfig(int maxLeadBars, int maxLagBars, HistoryPolicy historyPolicy,
        EmptyEventPolicy emptyEventPolicy) {

    /**
     * Creates a config with range clamping to the available history and the
     * undefined-when-both-empty event policy.
     *
     * <p>
     * {@link HistoryPolicy#CLAMP} is the default for this convenience constructor
     * because it makes the common full-series workflow
     * ({@code [getBeginIndex(), getEndIndex()]} over signals with nonzero
     * unstable-bar counts) safe without computing the stable intersection manually.
     * Use the full constructor when evaluation must fail fast on unavailable
     * history.
     *
     * @param maxLeadBars maximum bars a prediction may lead its reference
     * @param maxLagBars  maximum bars a prediction may lag its reference
     */
    public EventSynchronizationConfig(int maxLeadBars, int maxLagBars) {
        this(maxLeadBars, maxLagBars, HistoryPolicy.CLAMP, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY);
    }

    /**
     * Validates the configuration.
     */
    public EventSynchronizationConfig {
        if (maxLeadBars < 0) {
            throw new IllegalArgumentException("maxLeadBars must be >= 0");
        }
        if (maxLagBars < 0) {
            throw new IllegalArgumentException("maxLagBars must be >= 0");
        }
        Objects.requireNonNull(historyPolicy, "historyPolicy");
        Objects.requireNonNull(emptyEventPolicy, "emptyEventPolicy");
    }

    /**
     * How the evaluator treats requested history that is not available on the
     * series (constrained series begin index, unstable-bar boundaries, and series
     * end index).
     */
    public enum HistoryPolicy {
        /**
         * Fail fast when the requested evaluation range includes unavailable history.
         */
        STRICT,
        /**
         * Intersect the requested range with the available history.
         */
        CLAMP
    }

    /**
     * Metric semantics for the case where both event streams are empty.
     *
     * <p>
     * Partial-empty cases (exactly one stream empty) always produce the same
     * values: the empty side's metric is {@code NaN}, the other side is {@code 0},
     * and F1 is {@code 0}.
     */
    public enum EmptyEventPolicy {
        /**
         * Precision, recall, and F1 are {@code NaN} when both streams are empty.
         *
         * <p>
         * This is the default: it prevents an optimizer from achieving a perfect score
         * by configuring a signal that never fires against an empty target window.
         */
        UNDEFINED_WHEN_BOTH_EMPTY,
        /**
         * Precision, recall, and F1 are {@code 0} when both streams are empty.
         */
        ZERO_WHEN_BOTH_EMPTY,
        /**
         * Precision, recall, and F1 are {@code 1} when both streams are empty.
         */
        ONE_WHEN_BOTH_EMPTY
    }
}

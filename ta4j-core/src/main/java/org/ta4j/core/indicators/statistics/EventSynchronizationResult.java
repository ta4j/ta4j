/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Package-private outcome of one {@link EventSynchronizationSupport}
 * synchronization; not part of the public API.
 *
 * <p>
 * The record implements the public {@link EventSynchronizationIndicator.Result}
 * view: callers of {@link EventSynchronizationIndicator#getResult(int)} receive
 * this instance through the read-only interface and cannot manufacture or
 * mutate results.
 *
 * <p>
 * All lists are unmodifiable and ordered: {@link #matches()} follows the
 * chronological match order (predicted and reference indexes both ascending),
 * and the unmatched lists are ascending. Repeated evaluation on the same inputs
 * produces structurally equal results.
 *
 * <p>
 * Numeric outputs (precision, recall, F1, lag summaries) are produced by the
 * evaluated series' {@link org.ta4j.core.num.NumFactory}; counts and indexes
 * are primitive integers.
 *
 * @param requestedStartIndex       the requested inclusive evaluation start
 *                                  index
 * @param requestedEndIndex         the requested inclusive evaluation end index
 * @param effectiveStartIndex       the resolved inclusive start index actually
 *                                  evaluated
 * @param effectiveEndIndex         the resolved inclusive end index actually
 *                                  evaluated
 * @param predictedCount            the number of predicted events inside the
 *                                  effective range
 * @param referenceCount            the number of reference events inside the
 *                                  effective range
 * @param matchedCount              the number of matched pairs (true positives)
 * @param falsePositives            {@code predictedCount - matchedCount}
 * @param falseNegatives            {@code referenceCount - matchedCount}
 * @param precision                 {@code NaN} when there are no predicted
 *                                  events
 * @param recall                    {@code NaN} when there are no reference
 *                                  events
 * @param f1Score                   the F1 score, {@code 0} when either side is
 *                                  empty or nothing matched, {@code NaN} when
 *                                  both streams are empty
 * @param matches                   the matched pairs in chronological order
 * @param unmatchedPredictedIndexes the unmatched predicted event indexes in
 *                                  ascending order
 * @param unmatchedReferenceIndexes the unmatched reference event indexes in
 *                                  ascending order
 * @param exactMatchCount           the number of matches with
 *                                  {@code offsetBars == 0}
 * @param meanSignedOffset          the mean signed offset, {@code NaN} when
 *                                  nothing matched
 * @param meanAbsoluteOffset        the mean absolute offset, {@code NaN} when
 *                                  nothing matched
 * @param medianSignedOffset        the median signed offset, {@code NaN} when
 *                                  nothing matched
 * @param minSignedOffset           the minimum signed offset, {@code NaN} when
 *                                  nothing matched
 * @param maxSignedOffset           the maximum signed offset, {@code NaN} when
 *                                  nothing matched
 * @param windowAvailable           {@code true} when the effective range was
 *                                  non-empty and actually evaluated
 */
record EventSynchronizationResult(int requestedStartIndex, int requestedEndIndex, int effectiveStartIndex,
        int effectiveEndIndex, int predictedCount, int referenceCount, int matchedCount, int falsePositives,
        int falseNegatives, Num precision, Num recall, Num f1Score,
        List<EventSynchronizationIndicator.Result.Match> matches, List<Integer> unmatchedPredictedIndexes,
        List<Integer> unmatchedReferenceIndexes, int exactMatchCount, Num meanSignedOffset, Num meanAbsoluteOffset,
        Num medianSignedOffset, Num minSignedOffset, Num maxSignedOffset,
        boolean windowAvailable) implements EventSynchronizationIndicator.Result {

    /**
     * Validates the metric references and defensively copies the lists.
     */
    EventSynchronizationResult {
        Objects.requireNonNull(precision, "precision");
        Objects.requireNonNull(recall, "recall");
        Objects.requireNonNull(f1Score, "f1Score");
        Objects.requireNonNull(meanSignedOffset, "meanSignedOffset");
        Objects.requireNonNull(meanAbsoluteOffset, "meanAbsoluteOffset");
        Objects.requireNonNull(medianSignedOffset, "medianSignedOffset");
        Objects.requireNonNull(minSignedOffset, "minSignedOffset");
        Objects.requireNonNull(maxSignedOffset, "maxSignedOffset");
        matches = List.copyOf(matches);
        unmatchedPredictedIndexes = List.copyOf(unmatchedPredictedIndexes);
        unmatchedReferenceIndexes = List.copyOf(unmatchedReferenceIndexes);
    }

    @Override
    public int windowStartIndex() {
        return requestedStartIndex;
    }

    @Override
    public int windowEndIndex() {
        return requestedEndIndex;
    }
}

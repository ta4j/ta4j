/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.analysis.event.EventSynchronizationConfig.EmptyEventPolicy;
import org.ta4j.core.num.Num;

/**
 * Immutable outcome of one {@link EventSynchronizationEvaluator} evaluation.
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
 * @since 0.24.1
 */
public final class EventSynchronizationResult {

    private final int requestedStartIndex;
    private final int requestedEndIndex;
    private final int effectiveStartIndex;
    private final int effectiveEndIndex;
    private final int predictedCount;
    private final int referenceCount;
    private final int matchedCount;
    private final int falsePositives;
    private final int falseNegatives;
    private final Num precision;
    private final Num recall;
    private final Num f1Score;
    private final List<EventMatch> matches;
    private final List<Integer> unmatchedPredictedIndexes;
    private final List<Integer> unmatchedReferenceIndexes;
    private final int exactMatchCount;
    private final Num meanSignedOffset;
    private final Num meanAbsoluteOffset;
    private final Num medianSignedOffset;
    private final Num minSignedOffset;
    private final Num maxSignedOffset;
    private final EmptyEventPolicy emptyEventPolicy;

    EventSynchronizationResult(int requestedStartIndex, int requestedEndIndex, int effectiveStartIndex,
            int effectiveEndIndex, int predictedCount, int referenceCount, int matchedCount, int falsePositives,
            int falseNegatives, Num precision, Num recall, Num f1Score, List<EventMatch> matches,
            List<Integer> unmatchedPredictedIndexes, List<Integer> unmatchedReferenceIndexes, int exactMatchCount,
            Num meanSignedOffset, Num meanAbsoluteOffset, Num medianSignedOffset, Num minSignedOffset,
            Num maxSignedOffset, EmptyEventPolicy emptyEventPolicy) {
        this.requestedStartIndex = requestedStartIndex;
        this.requestedEndIndex = requestedEndIndex;
        this.effectiveStartIndex = effectiveStartIndex;
        this.effectiveEndIndex = effectiveEndIndex;
        this.predictedCount = predictedCount;
        this.referenceCount = referenceCount;
        this.matchedCount = matchedCount;
        this.falsePositives = falsePositives;
        this.falseNegatives = falseNegatives;
        this.precision = Objects.requireNonNull(precision, "precision");
        this.recall = Objects.requireNonNull(recall, "recall");
        this.f1Score = Objects.requireNonNull(f1Score, "f1Score");
        this.matches = List.copyOf(matches);
        this.unmatchedPredictedIndexes = List.copyOf(unmatchedPredictedIndexes);
        this.unmatchedReferenceIndexes = List.copyOf(unmatchedReferenceIndexes);
        this.exactMatchCount = exactMatchCount;
        this.meanSignedOffset = Objects.requireNonNull(meanSignedOffset, "meanSignedOffset");
        this.meanAbsoluteOffset = Objects.requireNonNull(meanAbsoluteOffset, "meanAbsoluteOffset");
        this.medianSignedOffset = Objects.requireNonNull(medianSignedOffset, "medianSignedOffset");
        this.minSignedOffset = Objects.requireNonNull(minSignedOffset, "minSignedOffset");
        this.maxSignedOffset = Objects.requireNonNull(maxSignedOffset, "maxSignedOffset");
        this.emptyEventPolicy = Objects.requireNonNull(emptyEventPolicy, "emptyEventPolicy");
    }

    /**
     * @return the requested inclusive evaluation start index
     */
    public int requestedStartIndex() {
        return requestedStartIndex;
    }

    /**
     * @return the requested inclusive evaluation end index
     */
    public int requestedEndIndex() {
        return requestedEndIndex;
    }

    /**
     * @return the resolved inclusive start index actually evaluated
     */
    public int effectiveStartIndex() {
        return effectiveStartIndex;
    }

    /**
     * @return the resolved inclusive end index actually evaluated
     */
    public int effectiveEndIndex() {
        return effectiveEndIndex;
    }

    /**
     * @return the number of predicted events inside the effective range
     */
    public int predictedCount() {
        return predictedCount;
    }

    /**
     * @return the number of reference events inside the effective range
     */
    public int referenceCount() {
        return referenceCount;
    }

    /**
     * @return the number of matched pairs (true positives)
     */
    public int matchedCount() {
        return matchedCount;
    }

    /**
     * @return {@code predictedCount - matchedCount}
     */
    public int falsePositives() {
        return falsePositives;
    }

    /**
     * @return {@code referenceCount - matchedCount}
     */
    public int falseNegatives() {
        return falseNegatives;
    }

    /**
     * @return precision ({@code NaN} when there are no predicted events)
     */
    public Num precision() {
        return precision;
    }

    /**
     * @return recall ({@code NaN} when there are no reference events)
     */
    public Num recall() {
        return recall;
    }

    /**
     * @return the F1 score, {@code 0} when either side is empty or nothing matched,
     *         {@code NaN} under {@link EmptyEventPolicy#UNDEFINED_WHEN_BOTH_EMPTY}
     *         when both streams are empty
     */
    public Num f1Score() {
        return f1Score;
    }

    /**
     * @return the matched pairs in chronological order
     */
    public List<EventMatch> matches() {
        return matches;
    }

    /**
     * @return the unmatched predicted event indexes in ascending order
     */
    public List<Integer> unmatchedPredictedIndexes() {
        return unmatchedPredictedIndexes;
    }

    /**
     * @return the unmatched reference event indexes in ascending order
     */
    public List<Integer> unmatchedReferenceIndexes() {
        return unmatchedReferenceIndexes;
    }

    /**
     * @return the number of matches with {@code offsetBars == 0}
     */
    public int exactMatchCount() {
        return exactMatchCount;
    }

    /**
     * @return the mean signed offset, {@code NaN} when nothing matched
     */
    public Num meanSignedOffset() {
        return meanSignedOffset;
    }

    /**
     * @return the mean absolute offset, {@code NaN} when nothing matched
     */
    public Num meanAbsoluteOffset() {
        return meanAbsoluteOffset;
    }

    /**
     * @return the median signed offset, {@code NaN} when nothing matched
     */
    public Num medianSignedOffset() {
        return medianSignedOffset;
    }

    /**
     * @return the minimum signed offset, {@code NaN} when nothing matched
     */
    public Num minSignedOffset() {
        return minSignedOffset;
    }

    /**
     * @return the maximum signed offset, {@code NaN} when nothing matched
     */
    public Num maxSignedOffset() {
        return maxSignedOffset;
    }

    /**
     * @return the empty-event policy applied to this result
     */
    public EmptyEventPolicy emptyEventPolicy() {
        return emptyEventPolicy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventSynchronizationResult that)) {
            return false;
        }
        return requestedStartIndex == that.requestedStartIndex && requestedEndIndex == that.requestedEndIndex
                && effectiveStartIndex == that.effectiveStartIndex && effectiveEndIndex == that.effectiveEndIndex
                && predictedCount == that.predictedCount && referenceCount == that.referenceCount
                && matchedCount == that.matchedCount && falsePositives == that.falsePositives
                && falseNegatives == that.falseNegatives && exactMatchCount == that.exactMatchCount
                && precision.equals(that.precision) && recall.equals(that.recall) && f1Score.equals(that.f1Score)
                && matches.equals(that.matches) && unmatchedPredictedIndexes.equals(that.unmatchedPredictedIndexes)
                && unmatchedReferenceIndexes.equals(that.unmatchedReferenceIndexes)
                && meanSignedOffset.equals(that.meanSignedOffset) && meanAbsoluteOffset.equals(that.meanAbsoluteOffset)
                && medianSignedOffset.equals(that.medianSignedOffset) && minSignedOffset.equals(that.minSignedOffset)
                && maxSignedOffset.equals(that.maxSignedOffset) && emptyEventPolicy == that.emptyEventPolicy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestedStartIndex, requestedEndIndex, effectiveStartIndex, effectiveEndIndex,
                predictedCount, referenceCount, matchedCount, falsePositives, falseNegatives, precision, recall,
                f1Score, matches, unmatchedPredictedIndexes, unmatchedReferenceIndexes, exactMatchCount,
                meanSignedOffset, meanAbsoluteOffset, medianSignedOffset, minSignedOffset, maxSignedOffset,
                emptyEventPolicy);
    }

    @Override
    public String toString() {
        return "EventSynchronizationResult{" + "requestedStartIndex=" + requestedStartIndex + ", requestedEndIndex="
                + requestedEndIndex + ", effectiveStartIndex=" + effectiveStartIndex + ", effectiveEndIndex="
                + effectiveEndIndex + ", predictedCount=" + predictedCount + ", referenceCount=" + referenceCount
                + ", matchedCount=" + matchedCount + ", falsePositives=" + falsePositives + ", falseNegatives="
                + falseNegatives + ", precision=" + precision + ", recall=" + recall + ", f1Score=" + f1Score
                + ", matches=" + matches + ", unmatchedPredictedIndexes=" + unmatchedPredictedIndexes
                + ", unmatchedReferenceIndexes=" + unmatchedReferenceIndexes + ", exactMatchCount=" + exactMatchCount
                + ", meanSignedOffset=" + meanSignedOffset + ", meanAbsoluteOffset=" + meanAbsoluteOffset
                + ", medianSignedOffset=" + medianSignedOffset + ", minSignedOffset=" + minSignedOffset
                + ", maxSignedOffset=" + maxSignedOffset + ", emptyEventPolicy=" + emptyEventPolicy + '}';
    }
}

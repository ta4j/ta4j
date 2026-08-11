/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling F1 scorer for two sparse Boolean event streams over the same
 * {@link BarSeries}.
 *
 * <p>
 * The two source indicators are ordinary {@code Indicator<Boolean>} instances
 * (only {@link Boolean#TRUE} counts as an event; {@code null} and {@code false}
 * are non-events) defined over the same bar series. At bar index {@code i} the
 * indicator evaluates the <em>closed trailing window</em>
 * {@code [i - barCount + 1, i]}: {@link #getValue(int)} is the F1 score of the
 * one-to-one matching inside that window, and {@link #getResult(int)} returns
 * the full diagnostics (counts, precision, recall, matched pairs with signed
 * offsets, unmatched event indexes, and lag summaries).
 *
 * <p>
 * Predicted and reference events are matched with the following lexicographic
 * objective:
 * </p>
 * <ol>
 * <li>maximize the number of matched pairs</li>
 * <li>among maximum-cardinality assignments, minimize the total absolute
 * offset</li>
 * <li>then minimize the worst absolute offset</li>
 * <li>then prefer the lexicographically earliest sequence of
 * {@code (predictedIndex, referenceIndex)} pairs</li>
 * </ol>
 *
 * <p>
 * A pair is eligible when {@code -maxLagBars <= offset <= maxLeadBars}, where
 * {@code offset = referenceIndex - predictedIndex} (positive means the
 * prediction leads the reference).
 *
 * <h2>Window and availability semantics</h2>
 * <p>
 * Only events inside the closed window participate: events near a window
 * boundary are intentionally censored, so a prediction near the window end can
 * never match a reference that occurs after the window end even when
 * {@code maxLeadBars} would otherwise permit it. This is the correct causal
 * behavior for a rolling indicator and preserves training/validation isolation.
 *
 * <p>
 * The value is {@code NaN} (and {@link #getResult(int)} reports undefined
 * metrics with no events) until the complete window is available: the index
 * must be at or after {@link #getCountOfUnstableBars()} and the whole window
 * must lie within the series' current {@code [getBeginIndex(), getEndIndex()]}
 * domain. A window that reaches below the begin index of a rolling series, or
 * past the series end, is therefore undefined rather than silently truncated.
 * The one conventional edge case follows the "undefined statistic means
 * {@code NaN}" design language: when both streams contain no events in the
 * window, precision, recall, and F1 are all {@code NaN}; when exactly one
 * stream is empty, the empty side's metric is {@code NaN} and F1 is {@code 0}.
 *
 * <h2>Performance</h2>
 * <p>
 * Event indexes are cached per source and extended incrementally as bars are
 * added, so each source is scanned once per bar regardless of how many windows
 * are evaluated; every window evaluation then extracts its events by binary
 * search and runs the baseline matcher in {@code O(|P| * |R|)} time and memory
 * for {@code |P|} predicted and {@code |R|} reference events inside the window.
 * Evaluating every index of an {@code N}-bar series therefore costs
 * {@code O(N)} scanning plus the per-window matching cost, which is small for
 * sparse streams. Repeated {@code getResult(index)} calls for the same index
 * recompute the matching (use {@link #getValue(int)} for the cached scalar).
 *
 * @since 0.24.2
 */
public final class EventSynchronizationIndicator extends CachedIndicator<Num> {

    private final Indicator<Boolean> predicted;
    private final Indicator<Boolean> reference;
    private final EventSignal predictedSignal;
    private final EventSignal referenceSignal;
    private final int barCount;
    private final int maxLeadBars;
    private final int maxLagBars;
    private final EventIndexCache predictedEvents = new EventIndexCache();
    private final EventIndexCache referenceEvents = new EventIndexCache();

    /**
     * Creates a synchronization indicator with a symmetric tolerance window.
     *
     * @param predicted     the predicted event indicator
     * @param reference     the reference event indicator
     * @param barCount      the number of bars in each trailing window, {@code >= 1}
     * @param toleranceBars the maximum absolute offset for a matched pair,
     *                      {@code >= 0}
     * @throws NullPointerException     if an indicator is null
     * @throws IllegalArgumentException if the indicators use different series,
     *                                  {@code barCount < 1}, or
     *                                  {@code toleranceBars < 0}
     */
    public EventSynchronizationIndicator(Indicator<Boolean> predicted, Indicator<Boolean> reference, int barCount,
            int toleranceBars) {
        this(predicted, reference, barCount, toleranceBars, toleranceBars);
    }

    /**
     * Creates a synchronization indicator with an asymmetric tolerance window.
     *
     * @param predicted   the predicted event indicator
     * @param reference   the reference event indicator
     * @param barCount    the number of bars in each trailing window, {@code >= 1}
     * @param maxLeadBars maximum bars a prediction may lead its reference,
     *                    {@code >= 0}
     * @param maxLagBars  maximum bars a prediction may lag its reference,
     *                    {@code >= 0}
     * @throws NullPointerException     if an indicator is null
     * @throws IllegalArgumentException if the indicators use different series,
     *                                  {@code barCount < 1}, or a tolerance is
     *                                  negative
     */
    public EventSynchronizationIndicator(Indicator<Boolean> predicted, Indicator<Boolean> reference, int barCount,
            int maxLeadBars, int maxLagBars) {
        super(requireSameSeries(predicted, reference));
        this.predicted = Objects.requireNonNull(predicted, "predicted");
        this.reference = Objects.requireNonNull(reference, "reference");
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1");
        }
        if (maxLeadBars < 0) {
            throw new IllegalArgumentException("maxLeadBars must be >= 0");
        }
        if (maxLagBars < 0) {
            throw new IllegalArgumentException("maxLagBars must be >= 0");
        }
        this.barCount = barCount;
        this.maxLeadBars = maxLeadBars;
        this.maxLagBars = maxLagBars;
        this.predictedSignal = EventSignals.fromIndicator(this.predicted);
        this.referenceSignal = EventSignals.fromIndicator(this.reference);
    }

    private static BarSeries requireSameSeries(Indicator<Boolean> predicted, Indicator<Boolean> reference) {
        Objects.requireNonNull(predicted, "predicted");
        Objects.requireNonNull(reference, "reference");
        return EventSynchronizationSupport.requireSameSeries(predicted.getBarSeries(), reference.getBarSeries());
    }

    /**
     * @return {@code max(predicted, reference unstable bars) + barCount - 1},
     *         saturated at {@link Integer#MAX_VALUE}
     */
    @Override
    public int getCountOfUnstableBars() {
        int maxUnstable = Math.max(predicted.getCountOfUnstableBars(), reference.getCountOfUnstableBars());
        return (int) Math.min(Integer.MAX_VALUE, (long) maxUnstable + barCount - 1);
    }

    /**
     * @param index the bar index
     * @return the F1 score of the closed trailing window
     *         {@code [index - barCount + 1, index]}, {@code NaN} while the window
     *         is unavailable
     */
    @Override
    protected Num calculate(int index) {
        return getResult(index).f1Score();
    }

    /**
     * Evaluates the closed trailing window {@code [index - barCount + 1, index]}
     * and returns its full diagnostics.
     *
     * <p>
     * This method recomputes the matching on every call; use {@link #getValue(int)}
     * for the cached F1 scalar. When the window is unavailable (before
     * {@link #getCountOfUnstableBars()} or outside the series' current domain), the
     * returned result reports the requested window with undefined ({@code NaN})
     * metrics and no events.
     *
     * @param index the bar index
     * @return the immutable window evaluation result
     */
    public Result getResult(int index) {
        int windowStart = index - barCount + 1;
        if (index < getCountOfUnstableBars() || windowStart < getBarSeries().getBeginIndex()
                || index > getBarSeries().getEndIndex()) {
            return undefinedResult(windowStart, index);
        }
        BarSeries series = getBarSeries();
        int beginIndex = series.getBeginIndex();
        predictedEvents.ensureScannedThrough(index, predictedSignal, beginIndex);
        referenceEvents.ensureScannedThrough(index, referenceSignal, beginIndex);
        int[] predictedWindowEvents = predictedEvents.slice(windowStart, index);
        int[] referenceWindowEvents = referenceEvents.slice(windowStart, index);
        return EventSynchronizationSupport
                .synchronize(predictedWindowEvents, referenceWindowEvents, windowStart, index, windowStart, index,
                        maxLeadBars, maxLagBars, series.numFactory())
                .toPublicResult();
    }

    private static Result undefinedResult(int windowStartIndex, int windowEndIndex) {
        return new Result(windowStartIndex, windowEndIndex, 0, 0, 0, 0, 0, NaN.NaN, NaN.NaN, NaN.NaN, List.of(),
                List.of(), List.of(), 0, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN);
    }

    /**
     * Immutable outcome of one window evaluation.
     *
     * <p>
     * All lists are unmodifiable and ordered: {@link #matches()} follows the
     * chronological match order (predicted and reference indexes both ascending),
     * and the unmatched lists are ascending. Numeric outputs are produced by the
     * evaluated series' {@link org.ta4j.core.num.NumFactory}.
     *
     * @param windowStartIndex          the inclusive first bar of the evaluated
     *                                  window
     * @param windowEndIndex            the inclusive last bar of the evaluated
     *                                  window
     * @param predictedCount            the number of predicted events inside the
     *                                  window
     * @param referenceCount            the number of reference events inside the
     *                                  window
     * @param matchedCount              the number of matched pairs (true positives)
     * @param falsePositives            {@code predictedCount - matchedCount}
     * @param falseNegatives            {@code referenceCount - matchedCount}
     * @param precision                 {@code NaN} when there are no predicted
     *                                  events or the window is unavailable
     * @param recall                    {@code NaN} when there are no reference
     *                                  events or the window is unavailable
     * @param f1Score                   the F1 score, {@code 0} when either side is
     *                                  empty or nothing matched, {@code NaN} when
     *                                  both streams are empty or the window is
     *                                  unavailable
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
     * @since 0.24.2
     */
    public record Result(int windowStartIndex, int windowEndIndex, int predictedCount, int referenceCount,
            int matchedCount, int falsePositives, int falseNegatives, Num precision, Num recall, Num f1Score,
            List<Match> matches, List<Integer> unmatchedPredictedIndexes, List<Integer> unmatchedReferenceIndexes,
            int exactMatchCount, Num meanSignedOffset, Num meanAbsoluteOffset, Num medianSignedOffset,
            Num minSignedOffset, Num maxSignedOffset) {

        /**
         * Defensively copies the lists and validates the metric references.
         */
        public Result {
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

        /**
         * One matched predicted-reference event pair.
         *
         * <p>
         * The signed lag is derived, never stored: {@link #offsetBars()} returns
         * {@code referenceIndex - predictedIndex}, so no redundant invalid state can be
         * constructed.
         *
         * @param predictedIndex the bar index of the predicted event, {@code >= 0}
         * @param referenceIndex the bar index of the reference event, {@code >= 0}
         */
        public record Match(int predictedIndex, int referenceIndex) {

            /**
             * Validates that both event indexes are nonnegative.
             */
            public Match {
                if (predictedIndex < 0) {
                    throw new IllegalArgumentException("predictedIndex must be >= 0, but was " + predictedIndex);
                }
                if (referenceIndex < 0) {
                    throw new IllegalArgumentException("referenceIndex must be >= 0, but was " + referenceIndex);
                }
            }

            /**
             * @return the signed lag {@code referenceIndex - predictedIndex}; positive
             *         means the prediction leads the reference, zero exact coincidence,
             *         negative means the prediction lags the reference
             */
            public int offsetBars() {
                return referenceIndex - predictedIndex;
            }
        }
    }

    /**
     * Ascending cache of one signal's event indexes over the series' retained
     * history.
     *
     * <p>
     * The cache scans forward as the series grows and compacts the head when a
     * rolling series drops bars. Scans never read below the signal's unstable
     * boundary, and window searches always start at or after the series begin index
     * (guaranteed by the availability gate), so dropped head entries are never
     * observable.
     */
    private static final class EventIndexCache {

        private int[] events = new int[16];
        private int size;
        private int scannedThrough = -1;

        /**
         * Extends the cache through {@code endIndex}, scanning only the bars not
         * scanned yet and never reading below {@code beginIndex} or the signal's
         * unstable boundary.
         *
         * @throws IllegalArgumentException when the signal fires more often than the
         *                                  baseline matcher's capacity allows
         */
        synchronized void ensureScannedThrough(int endIndex, EventSignal signal, int beginIndex) {
            if (size > 0 && events[0] < beginIndex) {
                int firstRetained = EventSynchronizationSupport.lowerBound(events, 0, size, beginIndex);
                int newSize = size - firstRetained;
                System.arraycopy(events, firstRetained, events, 0, newSize);
                size = newSize;
            }
            if (endIndex <= scannedThrough) {
                return;
            }
            int scanFrom = Math.max(Math.max(scannedThrough + 1, beginIndex), signal.getCountOfUnstableBars());
            for (int i = scanFrom;; i++) {
                if (signal.isEvent(i)) {
                    if (size == events.length) {
                        if ((long) events.length * 2 > EventSynchronizationSupport.MAX_MATCHING_CELLS) {
                            throw new IllegalArgumentException("event count exceeds the baseline matcher capacity of "
                                    + (EventSynchronizationSupport.MAX_MATCHING_CELLS / 1_000_000L)
                                    + " million cells (~128 MB of alignment arrays)");
                        }
                        events = Arrays.copyOf(events, events.length * 2);
                    }
                    events[size++] = i;
                }
                if (i == endIndex) {
                    break;
                }
            }
            scannedThrough = endIndex;
        }

        /**
         * @param from inclusive absolute index
         * @param to   inclusive absolute index
         * @return the cached event indexes inside {@code [from, to]}, ascending
         */
        synchronized int[] slice(int from, int to) {
            int first = EventSynchronizationSupport.lowerBound(events, 0, size, from);
            int end = EventSynchronizationSupport.upperBound(events, 0, size, to);
            return Arrays.copyOfRange(events, first, end);
        }
    }
}

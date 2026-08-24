/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics.event;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
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
 * {@link Result#windowAvailable()} reports whether the window was evaluated; an
 * available window whose streams contain no events keeps its zero counts and
 * {@code NaN} metrics (see {@link Result}). The one conventional edge case
 * follows the "undefined statistic means {@code NaN}" design language: when
 * both streams contain no events in the window, precision, recall, and F1 are
 * all {@code NaN}; when exactly one stream is empty, the empty side's metric is
 * {@code NaN} and F1 is {@code 0}.
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
 * <p>
 * The matcher is bounded by a hard capacity: a window whose alignment problem
 * needs more than {@value EventSynchronizationSupport#MAX_MATCHING_CELLS} cells
 * — that is, {@code (predicted events + 1) * (reference events + 1) > 8
 * million} — throws {@link IllegalArgumentException} from
 * {@link #getResult(int)} (and therefore from {@link #getValue(int)}), as does
 * a source whose cached event history would exceed the same limit. Sparse event
 * streams stay far below this bound; a dense stream that fires on most bars
 * inside a large window can exceed it, so callers must be prepared for the
 * exception or keep windows sparse enough.
 *
 * <p>
 * Both event caches are invalidated from the series'
 * {@link BarSeriesChangeSnapshot} when retained history is replaced, cleared,
 * or removed, and the two sources are rescanned under one observed revision so
 * a concurrent series mutation cannot combine events from different revisions.
 *
 * @since 0.24.2
 */
public final class EventSynchronizationIndicator extends CachedIndicator<Num> {

    private final Indicator<Boolean> predicted;
    private final Indicator<Boolean> reference;
    // Runtime-derived views of the durable indicator fields, rebuilt by the
    // constructor after deserialization; never part of the serialized form.
    private transient final EventSignal predictedSignal;
    private transient final EventSignal referenceSignal;
    private final int barCount;
    private final int maxLeadBars;
    private final int maxLagBars;
    // Package-private for the white-box cache-bound regression test.
    final transient EventIndexCache predictedEvents;
    final transient EventIndexCache referenceEvents;
    private transient final Object cacheLock = new Object();
    // Runtime cache-coordination state, rebuilt from scratch after
    // deserialization; never part of the serialized descriptor.
    private transient long observedRevision = -1L;
    private transient int observedRemovedThroughIndex = -1;

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
     * @since 0.24.2
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
     * @since 0.24.2
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
        this.predictedEvents = new EventIndexCache(barCount);
        this.referenceEvents = new EventIndexCache(barCount);
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
     * @return the first absolute index at which both sources are stable. Source
     *         instability counts bars from the series' retained head (the anchoring
     *         convention shared across rolling statistics), so the boundary sits at
     *         {@code beginIndex + unstableBars}; computed in long because unstable
     *         counts saturate near {@link Integer#MAX_VALUE}.
     */
    private long firstStableIndex() {
        return (long) getBarSeries().getBeginIndex()
                + Math.max(predicted.getCountOfUnstableBars(), reference.getCountOfUnstableBars());
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
     * @param index the bar index
     * @return the cached F1 score of the closed trailing window
     *         {@code [index - barCount + 1, index]}, {@code NaN} when the index
     *         lies outside the series' current {@code [getBeginIndex(),
     *         getEndIndex()]} domain or the window reaches below the sources'
     *         anchored stability boundary
     */
    @Override
    public Num getValue(int index) {
        BarSeries series = getBarSeries();
        if (index < series.getBeginIndex() || index > series.getEndIndex()) {
            // The inherited CachedIndicator contract maps pruned indexes below the
            // retained begin index to the first retained bar; this indicator's
            // window semantics instead make them undefined (NaN), matching
            // getResult's availability gate.
            return NaN.NaN;
        }
        long windowStartIndex = (long) index - barCount + 1L;
        if (windowStartIndex < firstStableIndex()) {
            // A cached F1 can outlive the window it was computed from: when the
            // rolling series advances its head, the sources' stability boundary
            // rises above the window start even though the index itself stays
            // in-domain. getResult already reports such windows as unavailable,
            // so the cached scalar must not contradict it.
            return NaN.NaN;
        }
        return super.getValue(index);
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
     * metrics, no events, and {@link Result#windowAvailable()}{@code == false}.
     *
     * @param index the bar index
     * @return the immutable window evaluation result
     * @throws IllegalArgumentException when a dense window's alignment problem
     *                                  exceeds the baseline matcher capacity (see
     *                                  the class documentation)
     * @throws IllegalStateException    when the series changes so often during
     *                                  evaluation that the coordinated retry
     *                                  budget is exhausted; the condition is
     *                                  deliberately not cached, so the next call
     *                                  re-evaluates the window
     * @since 0.24.2
     */
    public Result getResult(int index) {
        BarSeries series = getBarSeries();
        long windowStartIndex = (long) index - barCount + 1L;
        // Availability is decided on the window's first bar against the sources'
        // anchored unstable boundary, all in long: far-out-of-domain requests
        // (for example Integer.MIN_VALUE) must wrap neither the window start into
        // a bogus in-range index nor the comparison itself before the gate
        // rejects them.
        if (index < series.getBeginIndex() || index > series.getEndIndex() || windowStartIndex < firstStableIndex()) {
            return undefinedResult((int) Math.max(windowStartIndex, Integer.MIN_VALUE), index);
        }
        int windowStart = (int) windowStartIndex;
        int[] predictedWindowEvents;
        int[] referenceWindowEvents;
        synchronized (cacheLock) {
            int revisionChangeRetries = 0;
            predictedEvents.retainWindowStart(windowStart);
            referenceEvents.retainWindowStart(windowStart);
            while (true) {
                BarSeriesChangeSnapshot snapshot = series.getBarSeriesChangeSnapshot(observedRevision);
                if (snapshot.revision() != observedRevision
                        || snapshot.removedThroughIndex() != observedRemovedThroughIndex) {
                    reconcileEventCaches(snapshot);
                }
                // Availability and scanning bounds come from the same coherent
                // snapshot as the cache reconciliation: a rolling series that
                // drops its head between the outer availability check and this
                // snapshot must not scan (or report as available) a window that
                // now reaches below the retained begin index or below the
                // sources' retained-head warm-up boundary. The max with the
                // series' virtual begin index covers series that publish a
                // different begin domain than their retained-bar snapshots.
                int beginIndex = Math.max(snapshot.removedThroughIndex() + 1, series.getBeginIndex());
                long snapshotStableBoundary = (long) beginIndex
                        + Math.max(predicted.getCountOfUnstableBars(), reference.getCountOfUnstableBars());
                if (index > Math.max(snapshot.endIndex(), series.getEndIndex()) || windowStart < beginIndex
                        || windowStart < snapshotStableBoundary) {
                    // A cleared or truncated series can move the end index
                    // below the requested bar after the outer domain check;
                    // scanning would then read out-of-domain indexes.
                    return undefinedResult(windowStart, index);
                }
                try {
                    predictedEvents.ensureScannedThrough(index, predictedSignal, beginIndex);
                    referenceEvents.ensureScannedThrough(index, referenceSignal, beginIndex);
                } catch (RuntimeException scanFailure) {
                    // A bar-backed signal source can observe the series mid-
                    // mutation (for example a concurrent clear()) and throw
                    // before the post-scan snapshot comparison below. Retry the
                    // whole iteration when the series actually moved; rethrow
                    // otherwise, because a failure on an unchanged revision is
                    // a genuine defect rather than a lost race.
                    BarSeriesChangeSnapshot afterFailure = series.getBarSeriesChangeSnapshot(observedRevision);
                    if (afterFailure.revision() == snapshot.revision()
                            && afterFailure.removedThroughIndex() == snapshot.removedThroughIndex()) {
                        throw scanFailure;
                    }
                    if (++revisionChangeRetries > 1) {
                        // A source that mutates the series on every read can
                        // otherwise livelock this loop (and the cache lock with
                        // it). Throwing keeps the transient condition out of the
                        // scalar cache: a cached NaN would survive revisions that
                        // never touch this window, permanently masking a window
                        // whose next evaluation could succeed.
                        throw new IllegalStateException(
                                "event synchronization retry budget exhausted at index " + index
                                        + " after repeated series revisions");
                    }
                    // The mutation is accounted for; retry the iteration now
                    // so a single transient mutation still gets its full retry
                    // instead of double-consuming the budget below.
                    continue;
                }
                BarSeriesChangeSnapshot after = series.getBarSeriesChangeSnapshot(observedRevision);
                if (after.revision() == snapshot.revision()
                        && after.removedThroughIndex() == snapshot.removedThroughIndex()) {
                    break;
                }
                if (++revisionChangeRetries > 1) {
                    throw new IllegalStateException(
                            "event synchronization retry budget exhausted at index " + index
                                    + " after repeated series revisions");
                }
            }
            // Capture both event windows before leaving the coordinated critical
            // section: a concurrent evaluation for another index may otherwise
            // reset or evict the caches between the two slices, yielding predicted
            // and reference windows taken from different cache states.
            predictedWindowEvents = predictedEvents.slice(windowStart, index);
            referenceWindowEvents = referenceEvents.slice(windowStart, index);
        }
        return EventSynchronizationSupport.synchronize(predictedWindowEvents, referenceWindowEvents, windowStart, index,
                windowStart, index, maxLeadBars, maxLagBars, series.numFactory());
    }

    /**
     * Drops every cached event at or after the series' earliest changed index and
     * lowers the scan frontier so the next read rescans the revised region. The
     * prefix-removal case (dropped bars) is handled separately by
     * {@link #discardThrough(int)}.
     *
     * @param snapshot the series change snapshot since the last observed revision
     */
    private void reconcileEventCaches(BarSeriesChangeSnapshot snapshot) {
        predictedEvents.discardThrough(snapshot.removedThroughIndex());
        referenceEvents.discardThrough(snapshot.removedThroughIndex());
        predictedEvents.discardFrom(snapshot.earliestChangedIndex());
        referenceEvents.discardFrom(snapshot.earliestChangedIndex());
        observedRevision = snapshot.revision();
        observedRemovedThroughIndex = snapshot.removedThroughIndex();
    }

    private static Result undefinedResult(int windowStartIndex, int windowEndIndex) {
        return new EventSynchronizationResult(windowStartIndex, windowEndIndex, windowStartIndex, windowEndIndex, 0, 0,
                0, 0, 0, NaN.NaN, NaN.NaN, NaN.NaN, List.of(), List.of(), List.of(), 0, NaN.NaN, NaN.NaN, NaN.NaN,
                NaN.NaN, NaN.NaN, false);
    }

    /**
     * Read-only outcome of one window evaluation.
     *
     * <p>
     * Instances are produced by
     * {@link EventSynchronizationIndicator#getResult(int)} and cannot be
     * manufactured by callers. All lists are unmodifiable and ordered:
     * {@link #matches()} follows the chronological match order (predicted and
     * reference indexes both ascending), and the unmatched lists are ascending.
     * Numeric outputs are produced by the evaluated series'
     * {@link org.ta4j.core.num.NumFactory}.
     *
     * <p>
     * {@link #windowAvailable()} distinguishes an unavailable window (false) from
     * an available window whose streams contain no events (true, zero counts and
     * {@code NaN} metrics).
     *
     * @since 0.24.2
     */
    public interface Result {

        /**
         * @return the inclusive first bar of the evaluated window
         * @since 0.24.2
         */
        int windowStartIndex();

        /**
         * @return the inclusive last bar of the evaluated window
         * @since 0.24.2
         */
        int windowEndIndex();

        /**
         * @return the number of predicted events inside the window
         * @since 0.24.2
         */
        int predictedCount();

        /**
         * @return the number of reference events inside the window
         * @since 0.24.2
         */
        int referenceCount();

        /**
         * @return the number of matched pairs (true positives)
         * @since 0.24.2
         */
        int matchedCount();

        /**
         * @return {@code predictedCount - matchedCount}
         * @since 0.24.2
         */
        int falsePositives();

        /**
         * @return {@code referenceCount - matchedCount}
         * @since 0.24.2
         */
        int falseNegatives();

        /**
         * @return {@code NaN} when there are no predicted events or the window is
         *         unavailable
         * @since 0.24.2
         */
        Num precision();

        /**
         * @return {@code NaN} when there are no reference events or the window is
         *         unavailable
         * @since 0.24.2
         */
        Num recall();

        /**
         * @return the F1 score, {@code 0} when either side is empty or nothing matched,
         *         {@code NaN} when both streams are empty or the window is unavailable
         * @since 0.24.2
         */
        Num f1Score();

        /**
         * @return the matched pairs in chronological order
         * @since 0.24.2
         */
        List<Match> matches();

        /**
         * @return the unmatched predicted event indexes in ascending order
         * @since 0.24.2
         */
        List<Integer> unmatchedPredictedIndexes();

        /**
         * @return the unmatched reference event indexes in ascending order
         * @since 0.24.2
         */
        List<Integer> unmatchedReferenceIndexes();

        /**
         * @return the number of matches with {@code offsetBars == 0}
         * @since 0.24.2
         */
        int exactMatchCount();

        /**
         * @return the mean signed offset, {@code NaN} when nothing matched
         * @since 0.24.2
         */
        Num meanSignedOffset();

        /**
         * @return the mean absolute offset, {@code NaN} when nothing matched
         * @since 0.24.2
         */
        Num meanAbsoluteOffset();

        /**
         * @return the median signed offset, {@code NaN} when nothing matched
         * @since 0.24.2
         */
        Num medianSignedOffset();

        /**
         * @return the minimum signed offset, {@code NaN} when nothing matched
         * @since 0.24.2
         */
        Num minSignedOffset();

        /**
         * @return the maximum signed offset, {@code NaN} when nothing matched
         * @since 0.24.2
         */
        Num maxSignedOffset();

        /**
         * @return {@code true} when the window was evaluated; {@code false} when the
         *         window was unavailable (before
         *         {@link EventSynchronizationIndicator#getCountOfUnstableBars()} or
         *         reaching outside the series' current domain) and the metrics are
         *         undefined
         * @since 0.24.2
         */
        boolean windowAvailable();

        /**
         * One matched pair.
         *
         * @param predictedIndex the predicted event index
         * @param referenceIndex the reference event index
         * @since 0.24.2
         */
        record Match(int predictedIndex, int referenceIndex) {

            /**
             * Validates both indexes.
             * 
             * @since 0.24.2
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
             * @since 0.24.2
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
     *
     * <p>
     * The enclosing indicator invalidates the cache from the series'
     * {@link BarSeriesChangeSnapshot}: {@link #discardFrom(int)} drops events at or
     * after the earliest changed index and {@link #discardThrough(int)} drops
     * events below the first retained index. Both run before the next scan, so a
     * replaced, cleared, or prefix-removed history never serves stale events.
     */
    static final class EventIndexCache {

        private final int windowSize;
        int[] events = new int[16];
        int size;
        int scannedThrough = -1;
        /**
         * Absolute index below which cached events may have been evicted; {@code -1}
         * when nothing was evicted yet.
         */
        private int evictionFrontier = -1;

        EventIndexCache(int windowSize) {
            this.windowSize = windowSize;
        }

        /**
         * Extends the cache through {@code endIndex}, scanning only the bars not
         * scanned yet and never reading below {@code beginIndex} or the signal's
         * unstable boundary.
         *
         * <p>
         * When the evaluated index is the last scanned bar, that bar is re-read instead
         * of trusting the earlier scan: a live forming bar is revised in place
         * (replaced bar, added price/trade) rather than appended, and the cached event
         * state would otherwise stay stale after the revision. Series that publish
         * change snapshots already invalidate this case through
         * {@link #discardFrom(int)}; the re-read additionally covers series
         * implementations that mutate without publishing a revision.
         *
         * <p>
         * A scan is transactional: when the signal throws, the cache rolls back to its
         * pre-scan state, so a retry never appends the same events twice.
         *
         * @throws IllegalArgumentException when the signal fires more often than the
         *                                  baseline matcher's capacity allows
         */
        synchronized void ensureScannedThrough(int endIndex, EventSignal signal, int beginIndex) {
            int[] savedBacking = events;
            int[] savedEventPrefix = null;
            int savedSize = size;
            int savedScannedThrough = scannedThrough;
            int savedEvictionFrontier = evictionFrontier;
            try {
                if (size > 0 && events[0] < beginIndex) {
                    savedEventPrefix = snapshotEventPrefix();
                    int firstRetained = EventSynchronizationSupport.lowerBound(events, 0, size, beginIndex);
                    int newSize = size - firstRetained;
                    System.arraycopy(events, firstRetained, events, 0, newSize);
                    size = newSize;
                }
                if (endIndex <= scannedThrough) {
                    if (endIndex == scannedThrough) {
                        // The evaluated bar is the last scanned one and may have been
                        // revised in place; re-read it so a replaced forming bar never
                        // serves stale events. Bars below it are historical and stay
                        // cached.
                        if (size > 0 && events[size - 1] == endIndex) {
                            size--;
                        }
                        scannedThrough = endIndex - 1;
                        appendEvent(endIndex, signal);
                        scannedThrough = endIndex;
                    }
                    return;
                }
                // Instability counts bars from the series' retained head, so the
                // source's own warm-up boundary sits at beginIndex + unstable
                // (saturated in long): scanning below it would read the source's
                // unavailable lookback or cache its deterministic warm-up values.
                int scanFrom = (int) Math.max(Math.max(scannedThrough + 1L, beginIndex),
                        Math.min((long) beginIndex + signal.getCountOfUnstableBars(), Integer.MAX_VALUE));
                int frontier = endIndex - windowSize + 1;
                long evictionThreshold = Math.min(Math.max((long) windowSize * 2, 16L),
                        EventSynchronizationSupport.MAX_MATCHING_CELLS / 2);
                for (int i = scanFrom;; i++) {
                    appendEvent(i, signal);
                    if (i == endIndex) {
                        break;
                    }
                    // Evict below the requested window's first bar while catching up to
                    // a distant index rather than after the whole catch-up: a signal that
                    // fires every bar over a long gap would otherwise accumulate the
                    // entire history and trip the matcher's capacity limit. The threshold
                    // is capped at half the matcher capacity so a window wider than that
                    // still evicts before the events array's growth ceiling throws.
                    if (size > 0 && events[0] < frontier && size >= evictionThreshold) {
                        // Append-only scans can roll back from their saved size. Copy only
                        // the used prefix before an in-place eviction can shift it.
                        if (savedEventPrefix == null) {
                            savedEventPrefix = snapshotEventPrefix();
                        }
                        evictBelowPrefix(frontier);
                    }
                }
                scannedThrough = endIndex;
                // This final eviction cannot throw, so append-only scans avoid a snapshot
                // on the normal rolling path.
                evictBelow(frontier);
            } catch (RuntimeException e) {
                events = savedBacking;
                if (savedEventPrefix != null) {
                    System.arraycopy(savedEventPrefix, 0, events, 0, savedSize);
                }
                size = savedSize;
                scannedThrough = savedScannedThrough;
                evictionFrontier = savedEvictionFrontier;
                throw e;
            }
        }

        private int[] snapshotEventPrefix() {
            return Arrays.copyOf(events, size);
        }

        /**
         * Ensures the cache still serves windows starting at {@code windowStart}. When
         * the cache evicted events at or above that start (a backward evaluation after
         * a forward one), the cache is reset so the next
         * {@link #ensureScannedThrough(int, EventSignal, int)} rescans from scratch.
         *
         * @param windowStart the inclusive first bar of the requested window
         */
        synchronized void retainWindowStart(int windowStart) {
            if (windowStart < evictionFrontier) {
                size = 0;
                scannedThrough = -1;
                evictionFrontier = -1;
            }
        }

        /**
         * Drops every cached event below {@code frontier} — the first bar of the window
         * that was just scanned — and records that eviction. Windows only move forward
         * under rolling evaluation, so an event outside the last scanned window can
         * never participate in a later one; bounding the cache by the window keeps a
         * frequent source (for example one event per bar with a one-bar window) from
         * accumulating the whole history and hitting the matcher's capacity limit.
         *
         * @param frontier the inclusive lowest index that must stay cached
         */
        private void evictBelow(int frontier) {
            if (frontier > evictionFrontier) {
                evictionFrontier = frontier;
                evictBelowPrefix(frontier);
            }
        }

        /**
         * Drops every cached event below {@code frontier}, regardless of the recorded
         * eviction frontier. Unlike {@link #evictBelow(int)}, this may run repeatedly
         * for the same frontier while a long catch-up scan appends more below-frontier
         * events after the first drop.
         *
         * @param frontier the inclusive lowest index that must stay cached
         */
        private void evictBelowPrefix(int frontier) {
            if (size > 0 && events[0] < frontier) {
                int firstRetained = EventSynchronizationSupport.lowerBound(events, 0, size, frontier);
                int newSize = size - firstRetained;
                System.arraycopy(events, firstRetained, events, 0, newSize);
                size = newSize;
                // A distant catch-up can grow the backing array toward the
                // matcher's capacity ceiling before evicting nearly everything
                // (for example a 4,194,304-cell array emptied by an event-free
                // window ahead), which would otherwise retain tens of megabytes
                // per source for the rest of the evaluation. An eviction that
                // empties the cache resets to the initial capacity when the
                // array grew (an already-minimal array is reused, so the
                // rolling one-bar case never churns allocations), and a large
                // array that retains only a small fraction is trimmed to the
                // retained size.
                if (newSize == 0) {
                    if (events.length > 16) {
                        events = new int[16];
                    }
                } else if (events.length > 1_048_576 && newSize * 8 < events.length) {
                    events = Arrays.copyOf(events, Math.max(newSize, 16));
                }
            }
        }

        /**
         * Drops every cached event at or after {@code earliestChangedIndex} and lowers
         * the scan frontier to the preceding index so the next
         * {@link #ensureScannedThrough(int, EventSignal, int)} rescans the revised
         * region. {@code -1} means "no change" and is a no-op.
         *
         * @param earliestChangedIndex the inclusive earliest index whose bar content
         *                             changed, or {@code -1}
         */
        synchronized void discardFrom(int earliestChangedIndex) {
            if (earliestChangedIndex >= 0) {
                if (size > 0) {
                    int dropFrom = EventSynchronizationSupport.lowerBound(events, 0, size, earliestChangedIndex);
                    if (dropFrom < size) {
                        size = dropFrom;
                    }
                }
                if (earliestChangedIndex - 1 < scannedThrough) {
                    scannedThrough = earliestChangedIndex - 1;
                }
            }
        }

        /**
         * Drops every cached event at or below {@code removedThroughIndex}, the last
         * bar removed from the series head. {@code -1} means "nothing removed" and is a
         * no-op.
         *
         * @param removedThroughIndex the inclusive index of the last removed bar, or
         *                            {@code -1}
         */
        synchronized void discardThrough(int removedThroughIndex) {
            if (removedThroughIndex >= 0 && size > 0) {
                int keepFrom = EventSynchronizationSupport.upperBound(events, 0, size, removedThroughIndex);
                if (keepFrom > 0) {
                    int newSize = size - keepFrom;
                    System.arraycopy(events, keepFrom, events, 0, newSize);
                    size = newSize;
                }
            }
        }

        private void appendEvent(int index, EventSignal signal) {
            if (signal.isEvent(index)) {
                // Enforce the reserved-cell budget on every append, not only
                // when resizing: after the final clamped growth the array still
                // has room for one more element, which would otherwise slip
                // past the cap minus one contract. A one-sided alignment needs
                // count + 1 cells, so a single stream must stay at or below
                // the cap minus one events.
                if ((long) size + 2 > EventSynchronizationSupport.MAX_MATCHING_CELLS) {
                    throw new IllegalArgumentException("event count exceeds the baseline matcher capacity of "
                            + (EventSynchronizationSupport.MAX_MATCHING_CELLS / 1_000_000L)
                            + " million cells (~128 MB of alignment arrays)");
                }
                if (size == events.length) {
                    events = Arrays.copyOf(events,
                            (int) Math.min((long) events.length * 2, EventSynchronizationSupport.MAX_MATCHING_CELLS));
                }
                events[size++] = index;
            }
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

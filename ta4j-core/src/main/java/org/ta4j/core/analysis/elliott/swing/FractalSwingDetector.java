/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Swing detector backed by fractal swing high/low indicators.
 *
 * <p>
 * Use this detector when you prefer classic Bill Williams-style fractal
 * confirmations with configurable lookback/lookforward windows. It is the
 * default choice for deterministic swing detection in Elliott Wave analysis.
 *
 * @since 0.22.2
 */
public final class FractalSwingDetector implements SwingDetector {

    private final int lookbackLength;
    private final int lookforwardLength;
    private final int allowedEqualBars;

    /**
     * Upper bound on simultaneously retained series; replays evaluate one or few
     * series.
     */
    private static final int MAX_CACHED_SERIES = 4;

    /**
     * Shared incremental causal-replay state per (series, degree): rebuilding swing
     * detection for every as-of evaluation makes ascending replays merge the full
     * cumulative pivot prefix at every index. Each state keeps the merged
     * alternating pivot sequence between queries and absorbs only the newly
     * confirmed high/low pivots as an ascending replay advances, resetting on
     * detected series history changes or descending queries.
     */
    private final Map<SeriesKey, Map<ElliottDegree, CausalReplayState>> replayStates = Collections
            .synchronizedMap(new LinkedHashMap<SeriesKey, Map<ElliottDegree, CausalReplayState>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final Map.Entry<SeriesKey, Map<ElliottDegree, CausalReplayState>> eldest) {
                    return size() > MAX_CACHED_SERIES;
                }
            });

    /**
     * Creates a detector with symmetric lookback/lookforward windows.
     *
     * @param window number of bars to inspect before/after a pivot
     * @since 0.22.2
     */
    public FractalSwingDetector(final int window) {
        this(window, window, Math.min(window, window));
    }

    /**
     * Creates a detector with explicit lookback/lookforward windows.
     *
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param allowedEqualBars  maximum additional equal-value bars in a plateau
     * @since 0.22.2
     */
    public FractalSwingDetector(final int lookbackLength, final int lookforwardLength, final int allowedEqualBars) {
        if (lookbackLength < 1 || lookforwardLength < 1) {
            throw new IllegalArgumentException("Window lengths must be positive");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be non-negative");
        }
        this.lookbackLength = lookbackLength;
        this.lookforwardLength = lookforwardLength;
        this.allowedEqualBars = allowedEqualBars;
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        return replayStates.computeIfAbsent(new SeriesKey(series), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(degree,
                        ignored -> new CausalReplayState(series, lookbackLength, lookforwardLength, allowedEqualBars,
                                degree))
                .resultAt(clampedIndex);
    }

    @Override
    public List<SwingPivot> detectPivots(final BarSeries series, final int index) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            return List.of();
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        return replayStates.computeIfAbsent(new SeriesKey(series), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(ElliottDegree.MINUETTE,
                        ignored -> new CausalReplayState(series, lookbackLength, lookforwardLength, allowedEqualBars,
                                ElliottDegree.MINUETTE))
                .pivotsAt(clampedIndex);
    }

    /**
     * @return lookback window length
     * @since 0.22.2
     */
    public int getLookbackLength() {
        return lookbackLength;
    }

    /**
     * @return lookforward window length
     * @since 0.22.2
     */
    public int getLookforwardLength() {
        return lookforwardLength;
    }

    /**
     * @return allowed equal bars for flat tops/bottoms
     * @since 0.22.2
     */
    public int getAllowedEqualBars() {
        return allowedEqualBars;
    }

    /**
     * Per-(series, degree) incremental causal replay. The fractal high/low swing
     * indicators scan each newly observed bar once and expose the confirmed swing
     * points visible at an as-of index; this state keeps the merged alternating
     * pivot sequence between queries, so an ascending replay absorbs only the newly
     * confirmed pivots instead of rebuilding the cumulative prefix at every index.
     * Queries below the merged position or detected series history changes fall
     * back to one full re-merge, which is exactly a from-scratch detection.
     */
    private static final class CausalReplayState {

        private final BarSeries series;
        private RecentSwingIndicator swingHigh;
        private RecentSwingIndicator swingLow;
        private final ElliottDegree degree;

        /** Merged alternating pivots visible up to {@link #lastScannedIndex}. */
        private final List<Pivot> pivots = new ArrayList<>();

        /** Latest high/low swing indexes consumed by the causal replay cursor. */
        private int lastHighIndex = Integer.MIN_VALUE;
        private int lastLowIndex = Integer.MIN_VALUE;
        private int lastScannedIndex = Integer.MIN_VALUE;

        /**
         * Cached immutable detection result over {@link #pivots}; rebuilt only when a
         * merge mutation changed the tracked sequence, so unchanged replay bars reuse
         * one instance instead of re-materializing an {@link ElliottSwing} for every
         * accumulated pivot on every ascending query.
         */
        private SwingDetectorResult cachedResult;
        private List<SwingPivot> cachedPivots = List.of();
        private boolean pivotViewDirty = true;

        /** Whether {@link #pivots} changed since {@link #cachedResult} was built. */
        private boolean resultDirty = true;

        // History observation mirroring the swing indicators' own reset rules.
        // Revision-aware series changes are O(1); value snapshots are retained as
        // a fallback for direct Bar mutations, which the series revision cannot see.
        private long observedRevision;
        private int observedBeginIndex;
        private int observedEndIndex;
        private BarState observedLastBar;

        /**
         * OHLC snapshots per retained bar, indexed from {@link #observedBarBaseIndex}.
         * Direct {@link Bar} mutations can change a fractal's high/low input while
         * restoring its close, so all three prices are retained for fallback validation
         * on replay boundaries.
         */
        private final List<BarState> observedBars = new ArrayList<>();
        private int observedBarBaseIndex;

        private final int lookbackLength;
        private final int lookforwardLength;
        private final int allowedEqualBars;

        private CausalReplayState(final BarSeries series, final int lookbackLength, final int lookforwardLength,
                final int allowedEqualBars, final ElliottDegree degree) {
            this.series = series;
            this.lookbackLength = lookbackLength;
            this.lookforwardLength = lookforwardLength;
            this.degree = degree;
            this.allowedEqualBars = allowedEqualBars;
            this.swingHigh = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), lookbackLength,
                    lookforwardLength, allowedEqualBars);
            this.swingLow = new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), lookbackLength,
                    lookforwardLength, allowedEqualBars);
            observeSeries(true);
        }

        /**
         * Returns the detection result for {@code index}, extending the merged pivot
         * state incrementally when the query advances the as-of position.
         */
        private synchronized List<SwingPivot> pivotsAt(final int index) {
            advanceTo(index);
            if (pivotViewDirty) {
                cachedPivots = snapshotPivots();
                pivotViewDirty = false;
            }
            return cachedPivots;
        }

        private synchronized SwingDetectorResult resultAt(final int index) {
            advanceTo(index);
            if (resultDirty) {
                cachedResult = snapshot();
                resultDirty = false;
            }
            return cachedResult;
        }

        private void advanceTo(final int index) {
            final boolean replayRewinds = index < lastScannedIndex;
            if (replayRewinds || seriesHistoryChanged(index)) {
                reset(true);
            }
            final int beginIndex = series.getBeginIndex();
            final long scanStart = lastScannedIndex == Integer.MIN_VALUE ? beginIndex : (long) lastScannedIndex + 1L;
            long asOfIndex = scanStart;
            while (asOfIndex <= index) {
                final int observationIndex = (int) asOfIndex;
                final int highIndex = swingHigh.getLatestSwingIndex(observationIndex);
                final int lowIndex = swingLow.getLatestSwingIndex(observationIndex);
                final int mergedTailIndex = pivots.isEmpty() ? Integer.MIN_VALUE
                        : pivots.get(pivots.size() - 1).index();
                final boolean changedSidePrecedesMergedTail = !pivots.isEmpty()
                        && ((highIndex != lastHighIndex && highIndex < mergedTailIndex)
                                || (lowIndex != lastLowIndex && lowIndex < mergedTailIndex));
                if (highIndex < lastHighIndex || lowIndex < lastLowIndex || changedSidePrecedesMergedTail) {
                    // AbstractRecentSwingIndicator can retract or purge a newer
                    // confirmed point when a later scan discovers an older one.
                    // Rebuild the merged causal prefix and the indicators so the
                    // replayed observations cannot reuse confirmed-swing state
                    // damaged by those later scans.
                    resetMergedPivots();
                    rebuildIndicators();
                    for (long replayIndex = beginIndex; replayIndex <= observationIndex; replayIndex++) {
                        final int replayObservationIndex = (int) replayIndex;
                        processObservation(beginIndex, swingHigh.getLatestSwingIndex(replayObservationIndex),
                                swingLow.getLatestSwingIndex(replayObservationIndex));
                    }
                } else {
                    processObservation(beginIndex, highIndex, lowIndex);
                }
                lastScannedIndex = observationIndex;
                asOfIndex++;
            }
        }

        private void processObservation(final int beginIndex, final int highIndex, final int lowIndex) {
            final boolean newHigh = highIndex != lastHighIndex;
            final boolean newLow = lowIndex != lastLowIndex;

            if (newHigh && newLow && highIndex == lowIndex) {
                if (highIndex >= beginIndex) {
                    final Num highPrice = swingHigh.getPriceIndicator().getValue(highIndex);
                    final Num lowPrice = swingLow.getPriceIndicator().getValue(lowIndex);
                    final PivotType chosen;
                    if (pivots.isEmpty()) {
                        if (Num.isNaNOrNull(highPrice)) {
                            chosen = PivotType.LOW;
                        } else if (Num.isNaNOrNull(lowPrice)) {
                            chosen = PivotType.HIGH;
                        } else {
                            chosen = !highPrice.isLessThan(lowPrice) ? PivotType.HIGH : PivotType.LOW;
                        }
                    } else {
                        chosen = pivots.get(pivots.size() - 1).type().opposite();
                    }
                    absorb(chosen == PivotType.HIGH ? new Pivot(highIndex, highPrice, PivotType.HIGH)
                            : new Pivot(lowIndex, lowPrice, PivotType.LOW));
                }
            } else if (newHigh && newLow) {
                if (highIndex < lowIndex) {
                    if (highIndex >= beginIndex) {
                        absorb(new Pivot(highIndex, swingHigh.getPriceIndicator().getValue(highIndex), PivotType.HIGH));
                    }
                    if (lowIndex >= beginIndex) {
                        absorb(new Pivot(lowIndex, swingLow.getPriceIndicator().getValue(lowIndex), PivotType.LOW));
                    }
                } else {
                    if (lowIndex >= beginIndex) {
                        absorb(new Pivot(lowIndex, swingLow.getPriceIndicator().getValue(lowIndex), PivotType.LOW));
                    }
                    if (highIndex >= beginIndex) {
                        absorb(new Pivot(highIndex, swingHigh.getPriceIndicator().getValue(highIndex), PivotType.HIGH));
                    }
                }
            } else {
                if (newHigh && highIndex >= beginIndex) {
                    absorb(new Pivot(highIndex, swingHigh.getPriceIndicator().getValue(highIndex), PivotType.HIGH));
                }
                if (newLow && lowIndex >= beginIndex) {
                    absorb(new Pivot(lowIndex, swingLow.getPriceIndicator().getValue(lowIndex), PivotType.LOW));
                }
            }

            lastHighIndex = highIndex;
            lastLowIndex = lowIndex;
        }

        private void resetMergedPivots() {
            pivots.clear();
            lastHighIndex = Integer.MIN_VALUE;
            lastLowIndex = Integer.MIN_VALUE;
            resultDirty = true;
            pivotViewDirty = true;
        }

        private void absorb(final Pivot pivot) {
            if (Num.isNaNOrNull(pivot.price())) {
                return;
            }
            if (pivots.isEmpty()) {
                pivots.add(pivot);
                resultDirty = true;
                pivotViewDirty = true;
                return;
            }
            final Pivot last = pivots.get(pivots.size() - 1);
            if (last.type() == pivot.type()) {
                if (pivot.type() == PivotType.HIGH && !pivot.price().isLessThan(last.price())
                        || pivot.type() == PivotType.LOW && !pivot.price().isGreaterThan(last.price())) {
                    pivots.set(pivots.size() - 1, pivot);
                    resultDirty = true;
                    pivotViewDirty = true;
                }
                return;
            }
            if (last.index() == pivot.index()) {
                // High and low plateaus of different lengths can confirm
                // opposite-type sides at the same pivot index on different
                // bars. Appending would create a zero-length swing, so
                // reconcile with the tie rule of the simultaneous-update path.
                final Pivot winner = reconcileSharedIndex(last, pivot);
                if (winner != last) {
                    pivots.set(pivots.size() - 1, winner);
                    resultDirty = true;
                    pivotViewDirty = true;
                }
                return;
            }
            if (pivot.index() < last.index()) {
                // Staggered plateaus can confirm one side behind an
                // opposite-side pivot that already merged, including during
                // the fallback rebuild of the causal prefix. Withdraw the
                // trailing newer pivots, absorb the late arrival, then
                // re-merge the withdrawn ones so every replayed observation
                // keeps the merged sequence chronologically ordered.
                final List<Pivot> displaced = new ArrayList<>();
                while (!pivots.isEmpty() && pivots.get(pivots.size() - 1).index() > pivot.index()) {
                    displaced.add(pivots.remove(pivots.size() - 1));
                }
                absorb(pivot);
                for (int i = displaced.size() - 1; i >= 0; i--) {
                    absorb(displaced.get(i));
                }
                return;
            }
            pivots.add(pivot);
            resultDirty = true;
            pivotViewDirty = true;
        }

        /**
         * Reconciles two opposite-type pivots reported at the same index with the tie
         * rule the simultaneous-update branch applies: with a tracked predecessor, the
         * side alternating with that predecessor wins; otherwise the high side wins
         * unless its price sits below the low side.
         */
        private Pivot reconcileSharedIndex(final Pivot first, final Pivot second) {
            final boolean preferHigh;
            if (pivots.size() >= 2) {
                preferHigh = pivots.get(pivots.size() - 2).type() == PivotType.LOW;
            } else {
                final Num highPrice = first.type() == PivotType.HIGH ? first.price() : second.price();
                final Num lowPrice = first.type() == PivotType.HIGH ? second.price() : first.price();
                preferHigh = !highPrice.isLessThan(lowPrice);
            }
            if (preferHigh) {
                return first.type() == PivotType.HIGH ? first : second;
            }
            return first.type() == PivotType.LOW ? first : second;
        }

        private List<SwingPivot> snapshotPivots() {
            if (pivots.isEmpty()) {
                return List.of();
            }
            final List<SwingPivot> snapshot = new ArrayList<>(pivots.size());
            for (final Pivot pivot : pivots) {
                snapshot.add(new SwingPivot(pivot.index(), pivot.price(),
                        pivot.type() == PivotType.HIGH ? SwingPivotType.HIGH : SwingPivotType.LOW));
            }
            return List.copyOf(snapshot);
        }

        /** Builds the immutable swing chain over the merged pivots. */
        private SwingDetectorResult snapshot() {
            if (pivots.size() < 2) {
                return SwingDetectorResult.fromPivots(snapshotPivots(), degree);
            }
            final List<ElliottSwing> swings = new ArrayList<>(pivots.size() - 1);
            for (int i = 1; i < pivots.size(); i++) {
                final Pivot previous = pivots.get(i - 1);
                final Pivot current = pivots.get(i);
                swings.add(
                        new ElliottSwing(previous.index(), current.index(), previous.price(), current.price(), degree));
            }
            return SwingDetectorResult.fromSwings(swings);
        }

        private void reset(final boolean rebuildIndicators) {
            pivots.clear();
            lastHighIndex = Integer.MIN_VALUE;
            lastLowIndex = Integer.MIN_VALUE;
            lastScannedIndex = Integer.MIN_VALUE;
            cachedResult = null;
            cachedPivots = List.of();
            resultDirty = true;
            pivotViewDirty = true;
            if (rebuildIndicators) {
                rebuildIndicators();
            }
        }

        /** Replaces both swing indicators so their confirmed-swing state is rebuilt. */
        private void rebuildIndicators() {
            swingHigh = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), lookbackLength,
                    lookforwardLength, allowedEqualBars);
            swingLow = new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), lookbackLength,
                    lookforwardLength, allowedEqualBars);
        }

        /**
         * Detects series history changes with the same discipline the swing indicators
         * apply internally, so stale merge state never survives a mutation they would
         * themselves discard. Revision-aware series changes remain O(1). For legacy
         * series whose revisions cannot observe direct {@link Bar} mutations, the
         * retained OHLC snapshots are validated only when a replay rewinds, an append
         * extends the observed window, or a cached as-of result is queried again;
         * ordinary ascending replay therefore stays incremental.
         */
        private boolean seriesHistoryChanged(final int requestedIndex) {
            final long currentRevision = series.getBarHistoryRevision();
            final int currentBeginIndex = series.getBeginIndex();
            final int currentEndIndex = series.getEndIndex();
            final boolean revisionUnavailable = currentRevision < 0L || observedRevision < 0L;
            boolean changed = currentBeginIndex != observedBeginIndex
                    || (currentRevision >= 0L && observedRevision >= 0L && currentRevision != observedRevision)
                    || currentEndIndex < observedEndIndex;
            final boolean observedWindowExtended = currentEndIndex > observedEndIndex;
            final boolean validateRetainedBars = !changed && revisionUnavailable
                    && (observedWindowExtended || requestedIndex <= lastScannedIndex);
            if (!changed && validateRetainedBars) {
                changed = retainedBarsChanged(currentBeginIndex, Math.min(observedEndIndex, currentEndIndex));
            }
            if (!changed && !series.isEmpty() && currentEndIndex == observedEndIndex) {
                // The last bar is the live-forming bar: compare its full
                // high/low/close snapshot exactly.
                changed = !BarState.of(series.getLastBar()).sameAs(observedLastBar);
            }
            observeSeries(changed);
            return changed;
        }

        /**
         * Compares stored OHLC snapshots of previously observed retained bars against
         * the live bars. Bounds are clamped to the overlap of the captured window and
         * the current retention window so head removals or truncations can never index
         * outside the snapshot.
         */
        private boolean retainedBarsChanged(final int fromIndex, final int toIndex) {
            if (observedBars.isEmpty()) {
                return !series.isEmpty();
            }
            final long snapshotLast = (long) observedBarBaseIndex + observedBars.size() - 1L;
            final int begin = Math.max(fromIndex, observedBarBaseIndex);
            final int end = (int) Math.min((long) toIndex, snapshotLast);
            for (long index = begin; index <= (long) end; index++) {
                final int barIndex = (int) index;
                if (!observedBars.get(barIndex - observedBarBaseIndex).sameAs(BarState.of(series.getBar(barIndex)))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Recaptures or extends the revision, retention window, and value snapshots.
         */
        private void observeSeries(final boolean refreshBarSnapshot) {
            final int currentBeginIndex = series.getBeginIndex();
            final int previousObservedEndIndex = observedEndIndex;
            final int currentEndIndex = series.getEndIndex();
            final long currentRevision = series.getBarHistoryRevision();
            final boolean enteringLegacySnapshots = currentRevision < 0L && observedRevision >= 0L;
            final boolean snapshotBarValues = currentRevision < 0L || observedRevision < 0L;
            observedRevision = currentRevision;
            observedBeginIndex = currentBeginIndex;
            observedEndIndex = currentEndIndex;
            observedLastBar = series.isEmpty() ? null : BarState.of(series.getLastBar());
            if (!snapshotBarValues) {
                observedBars.clear();
            } else if (refreshBarSnapshot || enteringLegacySnapshots || series.isEmpty()) {
                observedBarBaseIndex = currentBeginIndex;
                observedBars.clear();
                for (long index = currentBeginIndex; index <= (long) currentEndIndex; index++) {
                    observedBars.add(BarState.of(series.getBar((int) index)));
                }
            } else {
                for (long index = Math.max((long) previousObservedEndIndex + 1L,
                        currentBeginIndex); index <= (long) currentEndIndex; index++) {
                    observedBars.add(BarState.of(series.getBar((int) index)));
                }
            }
        }
    }

    private record SeriesKey(BarSeries series) {

        @Override
        public boolean equals(final Object other) {
            return this == other || other instanceof SeriesKey key && series == key.series;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(series);
        }
    }

    private enum PivotType {
        HIGH, LOW;

        private PivotType opposite() {
            return this == HIGH ? LOW : HIGH;
        }
    }

    private record Pivot(int index, Num price, PivotType type) {
    }

    /**
     * Value snapshot of a retained bar's high/low/close prices. Series revision
     * tracking cannot observe in-place bar mutations such as
     * {@code series.getBar(index).addPrice(...)}, so replay fallback validation
     * compares these values exactly.
     */
    private record BarState(Num high, Num low, Num close) {

        private static BarState of(final Bar bar) {
            return new BarState(bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice());
        }

        private boolean sameAs(final BarState other) {
            return other != null && sameValue(high, other.high) && sameValue(low, other.low)
                    && sameValue(close, other.close);
        }

        private static boolean sameValue(final Num left, final Num right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            if (Num.isNaNOrNull(left) && Num.isNaNOrNull(right)) {
                return true;
            }
            return left.equals(right);
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Base class for swing-point indicators that exposes both swing values and
 * confirmed swing indexes.
 * <p>
 * Subclasses implement the swing-detection logic via
 * {@link #detectLatestSwingIndex(int)}. This base class handles caching of
 * swing indexes, purges indexes that fall out of the series window, and
 * provides access to swing-point values through the {@link Indicator} API.
 * Revisionless series validate consumed retained bar values before cached
 * queries and after scanning without the series read lock. A mismatch clears
 * the tracker and its cached source graph before replay; revision-aware history
 * checks remain constant-time.
 *
 * @since 0.20
 */
public abstract class AbstractRecentSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

    private static final Indicator<?>[] NO_ADDITIONAL_SOURCES = new Indicator<?>[0];

    private final Indicator<Num> priceIndicator;
    private final transient SwingPointTracker swingPoints;
    private final transient int unstableBars;

    /**
     * Creates a swing indicator that uses only its price source.
     *
     * @param priceIndicator the price indicator used to fetch swing values
     * @param unstableBars   number of unstable bars
     */
    protected AbstractRecentSwingIndicator(Indicator<Num> priceIndicator, int unstableBars) {
        this(priceIndicator, unstableBars, NO_ADDITIONAL_SOURCES);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator   the price indicator used to fetch swing values
     * @param unstableBars     number of unstable bars
     * @param sourceIndicators additional direct sources used to identify swings
     */
    protected AbstractRecentSwingIndicator(Indicator<Num> priceIndicator, int unstableBars,
            Indicator<?>... sourceIndicators) {
        super(priceIndicator, sourceIndicators);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator cannot be null");
        this.unstableBars = Math.max(0, unstableBars);
        final BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(),
                "priceIndicator.getBarSeries() cannot be null");
        this.swingPoints = new SwingPointTracker(this::detectLatestSwingIndex, series);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Validates the tracker even on an outer-cache hit and verifies the consumed
     * history again after fetching the swing value.
     *
     * @since 0.25.1
     */
    @Override
    public Num getValue(int index) {
        while (true) {
            swingPoints.getLatestSwingIndex(index);
            final Num value = super.getValue(index);
            if (swingPoints.historyStillCurrent()) {
                return value;
            }
        }
    }

    @Override
    public final int getLatestSwingIndex(int index) {
        return swingPoints.getLatestSwingIndex(index);
    }

    @Override
    public final int getLatestSwingConfirmationIndex(int index) {
        return swingPoints.getLatestSwingConfirmationIndex(index);
    }

    @Override
    public final List<Integer> getSwingPointIndexesUpTo(int index) {
        return swingPoints.getSwingPointIndexes(index);
    }

    @Override
    public final List<Integer> getSwingPointIndexes() {
        final BarSeries series = getBarSeries();
        return swingPoints.getSwingPointIndexes(series.getEndIndex());
    }

    /**
     * Whether the current thread holds the swing-point tracker monitor.
     *
     * <p>
     * Package-private for lock-order regression verification.
     *
     * @return {@code true} when the tracker monitor is held by the current thread
     */
    final boolean holdsSwingPointTrackerMonitor() {
        return Thread.holdsLock(swingPoints);
    }

    @Override
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    protected Num calculate(int index) {
        final BarSeries series = getBarSeries();
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        if (index < beginIndex || index > endIndex) {
            return NaN;
        }
        final int swingIndex = getLatestSwingIndex(index);
        if (swingIndex < beginIndex) {
            return NaN;
        }
        final Num swingValue = priceIndicator.getValue(swingIndex);
        return Num.isNaNOrNull(swingValue) ? NaN : swingValue;
    }

    /**
     * Returns the most recent confirmed swing point index that can be evaluated
     * using data up to the given index.
     *
     * @param index the current evaluation index
     * @return the latest confirmed swing index (monotonic, never exceeding the
     *         current {@code index}) or {@code -1} if no swing can be confirmed
     *         yet. Implementations should not move backwards once a swing is
     *         confirmed for a given window; use {@link #purgeOnNegativeDetection()}
     *         when a subclass needs to invalidate stale swings.
     */
    protected abstract int detectLatestSwingIndex(int index);

    /**
     * Whether a negative swing detection ({@code -1}) should clear previously
     * confirmed swings. Subclasses that invalidate stale swings (for example, when
     * a plateau grows beyond an equality allowance) can override to return
     * {@code true}. Default is {@code false}, so negative detections simply skip
     * adding a swing.
     *
     * @return {@code true} if negative detections should purge recorded swings
     */
    protected boolean purgeOnNegativeDetection() {
        return false;
    }

    private final class SwingPointTracker {
        private final IntFunction<Integer> swingIndexDetector;
        private final BarSeries series;
        private final List<ConfirmedSwing> confirmedSwings = new ArrayList<>();
        private int lastScannedIndex = Integer.MIN_VALUE;
        private long observedRevision;
        private int observedEndIndex;
        private int observedBeginIndex;
        private Bar observedLastBar;
        private HistorySnapshot observedHistory;
        private boolean sourceInvalidationPending;

        private SwingPointTracker(IntFunction<Integer> swingIndexDetector, BarSeries series) {
            this.swingIndexDetector = Objects.requireNonNull(swingIndexDetector, "swingIndexDetector cannot be null");
            this.series = Objects.requireNonNull(series, "series cannot be null");
            this.observedRevision = series.getBarHistoryRevision();
            this.observedEndIndex = series.getEndIndex();
            this.observedBeginIndex = series.getBeginIndex();
            this.observedLastBar = observedRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
        }

        private int getLatestSwingIndex(int index) {
            while (true) {
                synchronized (this) {
                    if (!ensureScanned(index)) {
                        final ConfirmedSwing latest = latestSwingAvailableAt(index);
                        return latest == null ? -1 : latest.swingIndex();
                    }
                }
                invalidateCacheAfterHistoryReset(true);
            }
        }

        private int getLatestSwingConfirmationIndex(int index) {
            while (true) {
                synchronized (this) {
                    if (!ensureScanned(index)) {
                        final ConfirmedSwing latest = latestSwingAvailableAt(index);
                        return latest == null ? -1 : latest.confirmationIndex();
                    }
                }
                invalidateCacheAfterHistoryReset(true);
            }
        }

        private List<Integer> getSwingPointIndexes(int index) {
            while (true) {
                synchronized (this) {
                    if (!ensureScanned(index)) {
                        final List<Integer> filtered = new ArrayList<>();
                        for (ConfirmedSwing swing : confirmedSwings) {
                            if (swing.confirmationIndex() <= index) {
                                filtered.add(swing.swingIndex());
                            }
                        }
                        return Collections.unmodifiableList(filtered);
                    }
                }
                invalidateCacheAfterHistoryReset(true);
            }
        }

        private synchronized boolean historyStillCurrent() {
            return series.withReadLock(() -> observedRevision == series.getBarHistoryRevision()
                    && observedBeginIndex == series.getBeginIndex() && observedEndIndex == series.getEndIndex()
                    && (observedHistory == null || observedHistory.retainedValuesMatch(series)));
        }

        /**
         * Invalidates the outer cache only after releasing this tracker monitor. Cache
         * calculations acquire the outer cache lock before querying this tracker.
         *
         * @param historyReset whether the tracker was reset for changed history
         */
        private void invalidateCacheAfterHistoryReset(boolean historyReset) {
            if (historyReset) {
                if (series.getBarHistoryRevision() < 0L) {
                    AbstractRecentSwingIndicator.this.invalidateCacheIncludingDependencies();
                } else {
                    AbstractRecentSwingIndicator.this.invalidateCache();
                }
                synchronized (this) {
                    sourceInvalidationPending = false;
                }
            }
        }

        private ConfirmedSwing latestSwingAvailableAt(int index) {
            for (int i = confirmedSwings.size() - 1; i >= 0; i--) {
                final ConfirmedSwing candidate = confirmedSwings.get(i);
                if (candidate.confirmationIndex() <= index) {
                    return candidate;
                }
            }
            return null;
        }

        private boolean ensureScanned(int index) {
            if (sourceInvalidationPending || resetIfHistoryChanged()) {
                sourceInvalidationPending = true;
                return true;
            }
            final int beginIndex = series.getBeginIndex();
            final int endIndex = series.getEndIndex();
            purgeOutOfRange(beginIndex);
            if (index < beginIndex || beginIndex > endIndex) {
                return false;
            }
            final int targetIndex = Math.min(index, endIndex);
            if (lastScannedIndex < beginIndex - 1) {
                lastScannedIndex = beginIndex - 1;
            }
            if (targetIndex <= lastScannedIndex) {
                return false;
            }
            final HistorySnapshot before = series.withReadLock(() -> HistorySnapshot.capture(series, targetIndex));
            final long firstIndex = Math.max((long) beginIndex, (long) lastScannedIndex + 1L);
            for (long currentIndex = firstIndex; currentIndex <= targetIndex; currentIndex++) {
                final int currentBarIndex = (int) currentIndex;
                final int swingIndex = swingIndexDetector.apply(currentBarIndex);
                if (swingIndex < 0) {
                    if (purgeOnNegativeDetection()) {
                        confirmedSwings.clear();
                    }
                    continue;
                }
                final boolean validSwing = swingIndex >= beginIndex && swingIndex <= currentBarIndex;
                if (!validSwing) {
                    continue;
                }
                while (!confirmedSwings.isEmpty()
                        && confirmedSwings.get(confirmedSwings.size() - 1).swingIndex() > swingIndex) {
                    confirmedSwings.remove(confirmedSwings.size() - 1);
                }
                if (confirmedSwings.isEmpty()
                        || swingIndex > confirmedSwings.get(confirmedSwings.size() - 1).swingIndex()) {
                    confirmedSwings.add(new ConfirmedSwing(swingIndex, currentBarIndex));
                }
            }
            if (!series.withReadLock(() -> before.matches(series))) {
                confirmedSwings.clear();
                lastScannedIndex = Integer.MIN_VALUE;
                observedHistory = null;
                sourceInvalidationPending = true;
                return true;
            }
            lastScannedIndex = targetIndex;
            observedEndIndex = before.endIndex();
            observedBeginIndex = before.beginIndex();
            observedRevision = before.revision();
            observedLastBar = before.lastBar();
            observedHistory = before;
            return false;
        }

        private boolean resetIfHistoryChanged() {
            return series.withReadLock(this::resetIfHistoryChangedUnderReadLock);
        }

        private boolean resetIfHistoryChangedUnderReadLock() {
            final long currentRevision = series.getBarHistoryRevision();
            final int currentBeginIndex = series.getBeginIndex();
            final int currentEndIndex = series.getEndIndex();
            final Bar currentLastBar = currentRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
            final boolean trackedRevisionChanged = currentRevision >= 0L && observedRevision >= 0L
                    && currentRevision != observedRevision;
            final boolean fallbackHistoryChanged = currentRevision < 0L && (currentEndIndex < observedEndIndex
                    || currentEndIndex == observedEndIndex && currentLastBar != observedLastBar
                    || observedHistory != null && !observedHistory.retainedValuesMatch(series));
            final boolean retainedRangeChanged = currentBeginIndex != observedBeginIndex;
            final boolean revisionSupportChanged = (currentRevision < 0L) != (observedRevision < 0L);
            if (!trackedRevisionChanged && !fallbackHistoryChanged && !retainedRangeChanged
                    && !revisionSupportChanged) {
                observedRevision = currentRevision;
                observedBeginIndex = currentBeginIndex;
                observedEndIndex = currentEndIndex;
                observedLastBar = currentLastBar;
                return false;
            }
            confirmedSwings.clear();
            lastScannedIndex = Integer.MIN_VALUE;
            observedHistory = null;
            observedRevision = currentRevision;
            observedBeginIndex = currentBeginIndex;
            observedEndIndex = currentEndIndex;
            observedLastBar = currentLastBar;
            return true;
        }

        private void purgeOutOfRange(int beginIndex) {
            if (confirmedSwings.isEmpty()) {
                return;
            }
            int firstRetained = 0;
            while (firstRetained < confirmedSwings.size()
                    && confirmedSwings.get(firstRetained).swingIndex() < beginIndex) {
                firstRetained++;
            }
            if (firstRetained > 0) {
                confirmedSwings.subList(0, firstRetained).clear();
            }
        }
    }

    /**
     * A bar-only observation; revision-aware series never allocate value snapshots.
     */
    private record HistorySnapshot(long revision, int beginIndex, int endIndex, Bar lastBar, List<BarState> bars) {
        private static HistorySnapshot capture(BarSeries series, int targetIndex) {
            final long revision = series.getBarHistoryRevision();
            final int begin = series.getBeginIndex();
            final int end = series.getEndIndex();
            final List<BarState> bars = revision < 0L ? new ArrayList<>() : List.of();
            if (revision < 0L && !series.isEmpty()) {
                for (long i = begin; i <= Math.min(targetIndex, end); i++) {
                    bars.add(BarState.capture(series.getBar((int) i)));
                }
            }
            return new HistorySnapshot(revision, begin, end,
                    revision < 0L && !series.isEmpty() ? series.getLastBar() : null, bars);
        }

        private boolean matches(BarSeries series) {
            return revision == series.getBarHistoryRevision() && beginIndex == series.getBeginIndex()
                    && endIndex == series.getEndIndex() && retainedValuesMatch(series);
        }

        private boolean retainedValuesMatch(BarSeries series) {
            if (revision >= 0L) {
                return true;
            }
            final long last = Math.min((long) beginIndex + bars.size() - 1L, series.getEndIndex());
            for (long i = Math.max(beginIndex, series.getBeginIndex()); i <= last; i++) {
                if (!bars.get((int) (i - beginIndex)).equals(BarState.capture(series.getBar((int) i)))) {
                    return false;
                }
            }
            return true;
        }
    }

    private record BarState(Num open, Num high, Num low, Num close, Num volume, Num amount, long trades,
            Duration period, Instant begin, Instant end) {
        private static BarState capture(Bar bar) {
            return new BarState(bar.getOpenPrice(), bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(),
                    bar.getVolume(), bar.getAmount(), bar.getTrades(), bar.getTimePeriod(), bar.getBeginTime(),
                    bar.getEndTime());
        }
    }

    private record ConfirmedSwing(int swingIndex, int confirmationIndex) {
    }
}

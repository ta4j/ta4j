/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Counts exact directional changes with a guarded sequential checkpoint.
 */
final class TrendStrengthIndicator extends CachedIndicator<Integer> {
    private final Indicator<Num> source;
    private final int barCount;
    private final boolean rising;
    // The nonterminal anchor exposes source-graph cache invalidation across
    // appends.
    private final ChangeAnchor anchor;
    private final ReentrantLock checkpointLock = new ReentrantLock();
    private volatile long checkpointVersion;
    private volatile long checkpointRevision = -1;
    private volatile int checkpointIndex = -1;
    private volatile int checkpointBegin;
    private volatile int checkpointStart;
    private volatile int checkpointCount;
    // The outgoing comparison can survive after its source bar is evicted.
    private volatile int checkpointFirst;
    private volatile Num checkpointValue;

    TrendStrengthIndicator(Indicator<Num> source, int barCount, boolean rising) {
        super(source);
        this.source = source;
        this.barCount = barCount;
        this.rising = rising;
        this.anchor = new ChangeAnchor(source);
    }

    int count(int index) {
        return getValue(index);
    }

    @Override
    protected Integer calculate(int index) {
        BarSeries series = getBarSeries();
        int begin = Math.max(0, series.getBeginIndex());
        if (index <= begin) {
            return 0;
        }

        boolean terminal = index == series.getEndIndex();
        if (terminal && barCount > 1 && series.getMaximumBarCount() != Integer.MAX_VALUE) {
            long version = checkpointVersion();
            BarSeriesChangeSnapshot snapshot = series.getBarSeriesChangeSnapshot(-1L);
            if (version >= 0 && snapshot.revision() >= 0 && snapshot.endIndex() == index) {
                anchor.getValue(index - 1);
                Num current = source.getValue(index);
                Integer result = advance(index, begin, snapshot, current, version);
                if (result != null) {
                    return result;
                }
                int scanned = cachedOrScan(index, begin, true);
                int start = Math.max(begin + 1, index - barCount + 1);
                int first = comparison(start);
                if (stable(snapshot, index, current)) {
                    publish(version, snapshot, index, begin, start, scanned, first, current);
                }
                return scanned;
            }
        }
        return cachedOrScan(index, begin, terminal);
    }

    private int cachedOrScan(int index, int begin, boolean terminal) {
        if (terminal) {
            // The former last-bar value is not stored in the main cache on append.
            getValue(index - 1);
        }
        Integer previous = getCachedValueOrNull(index - 1);
        if (previous != null) {
            int result = previous + comparison(index);
            int outgoing = index - barCount;
            return outgoing > begin ? result - comparison(outgoing) : result;
        }

        int start = Math.max(begin + 1, index - barCount + 1);
        int result = 0;
        for (int i = start;; i++) {
            result += comparison(i);
            if (i == index) {
                return result;
            }
        }
    }

    private int comparison(int index) {
        return comparison(source.getValue(index), source.getValue(index - 1));
    }

    private int comparison(Num current, Num previous) {
        return (rising ? current.isGreaterThan(previous) : current.isLessThan(previous)) ? 1 : 0;
    }

    private long checkpointVersion() {
        if (!checkpointLock.tryLock()) {
            return -1;
        }
        try {
            return checkpointVersion;
        } finally {
            checkpointLock.unlock();
        }
    }

    private Integer advance(int index, int begin, BarSeriesChangeSnapshot snapshot, Num current, long version) {
        int previousIndex;
        int previousBegin;
        int previousStart;
        int previousCount;
        int previousFirst;
        long previousRevision;
        Num previousValue;
        if (!checkpointLock.tryLock()) {
            return null;
        }
        try {
            if (checkpointVersion != version) {
                return null;
            }
            previousIndex = checkpointIndex;
            previousBegin = checkpointBegin;
            previousStart = checkpointStart;
            previousCount = checkpointCount;
            previousFirst = checkpointFirst;
            previousRevision = checkpointRevision;
            previousValue = checkpointValue;
        } finally {
            checkpointLock.unlock();
        }
        if (previousRevision < 0 || previousIndex != index - 1 || previousIndex < begin) {
            return null;
        }
        int start = Math.max(begin + 1, index - barCount + 1);
        if ((start != previousStart && start != previousStart + 1)
                || (begin != previousBegin && (long) start - 1 < (long) begin + source.getCountOfUnstableBars())) {
            return null;
        }
        BarSeries series = getBarSeries();
        BarSeriesChangeSnapshot changes = series.getBarSeriesChangeSnapshot(previousRevision);
        if (changes.revision() < 0 || changes.endIndex() != index
                || (changes.earliestChangedIndex() >= 0 && changes.earliestChangedIndex() <= previousIndex)
                || !anchor.reusable(previousIndex - 1)) {
            return null;
        }
        // The previous terminal lies beyond the old anchor and needs its own check.
        Num previous = source.getValue(previousIndex);
        if (!Objects.equals(previousValue, previous)) {
            return null;
        }
        int result = previousCount + comparison(current, previous);
        int first = previousFirst;
        if (start != previousStart) {
            result -= previousFirst;
            first = start == index ? comparison(current, previous) : comparison(start);
        }
        if (!stable(snapshot, index, current) || !anchor.reusable(previousIndex - 1)
                || !Objects.equals(previous, source.getValue(previousIndex)) || !anchor.reusable(index - 1)
                || !sameState(snapshot, series.getBarSeriesChangeSnapshot(snapshot.revision()))) {
            return null;
        }
        publish(version, snapshot, index, begin, start, result, first, current);
        return result;
    }

    private boolean stable(BarSeriesChangeSnapshot snapshot, int index, Num current) {
        BarSeries series = getBarSeries();
        return sameState(snapshot, series.getBarSeriesChangeSnapshot(snapshot.revision())) && anchor.reusable(index - 1)
                && Objects.equals(current, source.getValue(index))
                && sameState(snapshot, series.getBarSeriesChangeSnapshot(snapshot.revision()))
                && anchor.reusable(index - 1);
    }

    private static boolean sameState(BarSeriesChangeSnapshot left, BarSeriesChangeSnapshot right) {
        return left.revision() == right.revision() && left.endIndex() == right.endIndex()
                && left.removedThroughIndex() == right.removedThroughIndex()
                && left.maximumBarCount() == right.maximumBarCount();
    }

    private void publish(long version, BarSeriesChangeSnapshot snapshot, int index, int begin, int start, int count,
            int first, Num value) {
        if (!checkpointLock.tryLock()) {
            return;
        }
        try {
            if (checkpointVersion == version) {
                checkpointRevision = snapshot.revision();
                checkpointIndex = index;
                checkpointBegin = begin;
                checkpointStart = start;
                checkpointCount = count;
                checkpointFirst = first;
                checkpointValue = value;
                checkpointVersion++;
            }
        } finally {
            checkpointLock.unlock();
        }
    }

    private static final class ChangeAnchor extends CachedIndicator<Boolean> {
        private final Indicator<Num> source;

        private ChangeAnchor(Indicator<Num> source) {
            super(source);
            this.source = source;
        }

        @Override
        protected Boolean calculate(int index) {
            return Boolean.TRUE;
        }

        @Override
        public int getCountOfUnstableBars() {
            return source.getCountOfUnstableBars();
        }

        private boolean reusable(int index) {
            synchronizeCacheWithSeries(getBarSeries());
            return isCached(index);
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return (int) Math.min(Integer.MAX_VALUE, (long) source.getCountOfUnstableBars() + barCount);
    }
}

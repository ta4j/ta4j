/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/** Tracks the newest occurrence of an extreme in an Aroon window. */
final class AroonExtremeIndexIndicator extends CachedIndicator<Integer> {
    private record Entry(int index, Num value) {
    }

    private final Indicator<Num> source;
    private final int barCount;
    private final boolean maximum;
    private final ReentrantLock lock = new ReentrantLock();
    private Deque<Entry> entries = new ArrayDeque<>();
    private volatile int checkpoint = -1;
    private volatile long revision = -1;

    AroonExtremeIndexIndicator(Indicator<Num> source, int barCount, boolean maximum) {
        super(source);
        this.source = source;
        this.barCount = barCount;
        this.maximum = maximum;
    }

    @Override
    protected Integer calculate(int index) {
        BarSeries series = source.getBarSeries();
        var snapshot = synchronizeCacheWithSeries(series);
        int begin = Math.max(0, series.getBeginIndex());
        int start = Math.max(begin, index - barCount);
        Num current = source.getValue(index);
        boolean terminal = index == snapshot.endIndex();
        if (terminal && index > begin) {
            getValue(index - 1);
        }
        int expectedIndex = checkpoint;
        long expectedRevision = revision;
        var changes = series.getBarSeriesChangeSnapshot(expectedRevision);
        boolean cached = expectedIndex >= 0 && isCached(expectedIndex);
        Num previous = terminal && expectedIndex == index - 2 ? source.getValue(index - 1) : null;
        if (cached && expectedRevision >= 0 && changes.revision() >= 0 && lock.tryLock()) {
            try {
                if (checkpoint == expectedIndex && revision == expectedRevision
                        && (changes.earliestChangedIndex() < 0 || changes.earliestChangedIndex() > checkpoint)
                        && changes.removedThroughIndex() < index
                        && (index == checkpoint + 1 || (terminal && index == checkpoint + 2 && previous != null))) {
                    if (index == checkpoint + 1) {
                        if (!terminal) {
                            add(entries, index, current);
                            checkpoint = index;
                        }
                    } else {
                        add(entries, index - 1, previous);
                        checkpoint = index - 1;
                    }
                    while (!entries.isEmpty() && entries.peekFirst().index() < start) {
                        entries.removeFirst();
                    }
                    revision = changes.revision();
                    Entry best = entries.peekFirst();
                    if (best == null) {
                        return current.isEqual(current) ? index : -1;
                    }
                    if (terminal && !current.isNaN() && betterOrEqual(current, best.value())) {
                        return current.isEqual(current) ? index : -1;
                    }
                    return matchingIndex(best);
                }
            } finally {
                lock.unlock();
            }
        }
        return rebuild(start, index, current, snapshot.revision(), terminal);
    }

    private int rebuild(int start, int index, Num current, long sourceRevision, boolean terminal) {
        Deque<Entry> rebuilt = new ArrayDeque<>();
        for (int i = start; i < index; i++) {
            add(rebuilt, i, source.getValue(i));
        }
        add(rebuilt, index, current);
        Entry best = rebuilt.peekFirst();
        int result = best == null ? (current.isEqual(current) ? index : -1) : matchingIndex(best);
        if (!terminal && sourceRevision >= 0 && lock.tryLock()) {
            try {
                entries = rebuilt;
                checkpoint = index;
                revision = sourceRevision;
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    private void add(Deque<Entry> queue, int index, Num value) {
        if (value.isNaN()) {
            return;
        }
        while (!queue.isEmpty() && betterOrEqual(value, queue.peekLast().value())) {
            queue.removeLast();
        }
        queue.addLast(new Entry(index, value));
    }

    private static int matchingIndex(Entry entry) {
        // A raw DoubleNum NaN participates in ordering but cannot match the scan's
        // equality test.
        return entry.value().isEqual(entry.value()) ? entry.index() : -1;
    }

    private boolean betterOrEqual(Num left, Num right) {
        return maximum ? left.isGreaterThanOrEqual(right) : left.isLessThanOrEqual(right);
    }

    @Override
    public int getCountOfUnstableBars() {
        return source.getCountOfUnstableBars() + barCount;
    }
}

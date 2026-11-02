/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/** Position-scoped extrema; changing positions discards the prior window. */
final class PositionPriceExtrema {
    private record Window(int entry, boolean maximum, Values values) {
    }

    private final Indicator<Num> source;
    private final boolean skipNull;
    private final int barCount;
    private volatile Window window;

    PositionPriceExtrema(Indicator<Num> source, int barCount) {
        this(source, barCount, true);
    }

    PositionPriceExtrema(Indicator<Num> source, int barCount, boolean skipNull) {
        this.source = source;
        this.barCount = barCount;
        this.skipNull = skipNull;
    }

    Num value(int index, int entry, boolean maximum) {
        Window selected = window;
        if (selected == null || selected.entry() != entry || selected.maximum() != maximum) {
            selected = new Window(entry, maximum, new Values(source, barCount, entry, maximum, skipNull));
            window = selected;
        }
        return selected.values().getValue(index);
    }

    private static final class Values extends CachedIndicator<Num> {
        private record Entry(int index, Num value) {
        }

        private final Indicator<Num> source;
        private final int barCount;
        private final int entry;
        private final boolean skipNull;
        private final boolean maximum;
        private final ReentrantLock lock = new ReentrantLock();
        private Deque<Entry> entries = new ArrayDeque<>();
        private volatile int checkpoint = -1;
        private volatile long revision = -1;

        Values(Indicator<Num> source, int barCount, int entry, boolean maximum, boolean skipNull) {
            super(source);
            this.source = source;
            this.barCount = barCount;
            this.entry = entry;
            this.maximum = maximum;
            this.skipNull = skipNull;
        }

        @Override
        protected Num calculate(int index) {
            BarSeries series = source.getBarSeries();
            var snapshot = synchronizeCacheWithSeries(series);
            int start = (int) Math.max(Math.max((long) entry, series.getBeginIndex()), (long) index - barCount + 1);
            Num current = source.getValue(index);
            if (!skipNull && current == null) {
                if (index > entry && barCount > 1) {
                    throw new NullPointerException();
                }
                return NaN.NaN;
            }
            boolean terminal = index == snapshot.endIndex();
            if (terminal && index > start) {
                getValue(index - 1);
            }
            int expected = checkpoint;
            long expectedRevision = revision;
            var changes = series.getBarSeriesChangeSnapshot(expectedRevision);
            Num previous = terminal && expected == index - 2 ? source.getValue(index - 1) : null;
            if (!skipNull && terminal && expected == index - 2) {
                Objects.requireNonNull(previous);
            }
            if (expected >= 0 && expectedRevision >= 0 && changes.revision() >= 0 && isCached(expected)
                    && lock.tryLock()) {
                try {
                    if (checkpoint == expected && revision == expectedRevision
                            && (changes.earliestChangedIndex() < 0 || changes.earliestChangedIndex() > expected)
                            && changes.removedThroughIndex() < index
                            && (index == expected + 1 || (terminal && index == expected + 2))) {
                        if (index == expected + 1) {
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
                        Num best = entries.isEmpty() ? NaN.NaN : entries.peekFirst().value();
                        if (terminal && !Num.isNaNOrNull(current) && (best.isNaN() || better(current, best))) {
                            best = current;
                        }
                        return best;
                    }
                } finally {
                    lock.unlock();
                }
            }
            return rebuild(start, index, current, snapshot.revision(), terminal);
        }

        private Num rebuild(int start, int index, Num current, long observedRevision, boolean terminal) {
            Deque<Entry> replacement = new ArrayDeque<>();
            for (int i = start; i < index; i++) {
                Num value = source.getValue(i);
                if (!skipNull) {
                    Objects.requireNonNull(value);
                }
                add(replacement, i, value);
            }
            add(replacement, index, current);
            Num result = replacement.isEmpty() ? NaN.NaN : replacement.peekFirst().value();
            if (!terminal && observedRevision >= 0 && lock.tryLock()) {
                try {
                    entries = replacement;
                    checkpoint = index;
                    revision = observedRevision;
                } finally {
                    lock.unlock();
                }
            }
            return result;
        }

        private void add(Deque<Entry> queue, int index, Num value) {
            if (Num.isNaNOrNull(value)) {
                return;
            }
            while (!queue.isEmpty() && better(value, queue.peekLast().value())) {
                queue.removeLast();
            }
            queue.addLast(new Entry(index, value));
        }

        private boolean better(Num left, Num right) {
            return maximum ? left.isGreaterThan(right) : left.isLessThan(right);
        }

        @Override
        protected int minimumCacheableIndexAfterHeadAdvance(int firstRetainedIndex) {
            long windowFloor = (long) super.minimumCacheableIndexAfterHeadAdvance(firstRetainedIndex) + barCount - 1;
            return (int) Math.min(Integer.MAX_VALUE, windowFloor);
        }

        @Override
        public int getCountOfUnstableBars() {
            return source.getCountOfUnstableBars();
        }
    }
}

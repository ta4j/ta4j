/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Private sequential state; the owning indicator's cache validates its inputs.
 */
final class RollingExtrema {
    private record Entry(int index, Num value) {
    }

    private final boolean maximum;
    private final ReentrantLock lock = new ReentrantLock();
    private Deque<Entry> entries = new ArrayDeque<>();
    private volatile int index = -1;
    private volatile long revision = -1;

    RollingExtrema(boolean maximum) {
        this.maximum = maximum;
    }

    int index() {
        return index;
    }

    long revision() {
        return revision;
    }

    Num advance(int expectedIndex, long expectedRevision, BarSeriesChangeSnapshot change, boolean cached, int target,
            int start, Num current, Num previous, boolean terminal) {
        if (!cached || expectedRevision < 0 || change.revision() < 0 || !lock.tryLock()) {
            return null;
        }
        try {
            if (index != expectedIndex || revision != expectedRevision
                    || (change.earliestChangedIndex() >= 0 && change.earliestChangedIndex() <= index)
                    || change.removedThroughIndex() >= target) {
                return null;
            }
            if (target == index + 1) {
                if (!terminal) {
                    add(entries, target, current);
                    index = target;
                }
            } else if (terminal && target == index + 2 && previous != null) {
                add(entries, target - 1, previous);
                index = target - 1;
            } else {
                return null;
            }
            while (!entries.isEmpty() && entries.peekFirst().index() < start) {
                entries.removeFirst();
            }
            revision = change.revision();
            Num best = entries.isEmpty() ? current : entries.peekFirst().value();
            if (terminal && !current.isNaN() && (best.isNaN() || betterOrEqual(current, best))) {
                best = current;
            }
            return best;
        } finally {
            lock.unlock();
        }
    }

    Num rebuild(Indicator<Num> source, int start, int target, Num current, long sourceRevision, boolean terminal) {
        Deque<Entry> rebuilt = new ArrayDeque<>();
        for (int i = start; i < target; i++) {
            add(rebuilt, i, source.getValue(i));
        }
        add(rebuilt, target, current);
        Num result = rebuilt.isEmpty() ? current : rebuilt.peekFirst().value();
        if (!terminal && sourceRevision >= 0 && lock.tryLock()) {
            try {
                entries = rebuilt;
                index = target;
                revision = sourceRevision;
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    private void add(Deque<Entry> queue, int at, Num value) {
        if (value.isNaN()) {
            return;
        }
        while (!queue.isEmpty() && betterOrEqual(value, queue.peekLast().value())) {
            queue.removeLast();
        }
        queue.addLast(new Entry(at, value));
    }

    private boolean betterOrEqual(Num left, Num right) {
        return maximum ? left.isGreaterThanOrEqual(right) : left.isLessThanOrEqual(right);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Threshold-notification replay shared by the percentage-change indicators.
 *
 * <p>
 * Keeps the trajectory of the last replay: {@code states[m]} is the
 * notification before bar {@code begin + m}. A replay from another start state
 * usually rejoins that trajectory within a few bars, so a full pass costs about
 * one source read per bar instead of rescanning the whole prefix for each one.
 * The trajectory is dropped when any series in the source graph changes its
 * history, and not kept at all when one of them does not track revisions.
 */
final class NotificationReplay {

    @FunctionalInterface
    interface Step {
        Num next(Num notification, int index);
    }

    private final Indicator<Num> source;
    private BarSeries[] graphSeries;
    private Num[] states;
    private int length;
    private int begin;
    private long[] revisions;

    NotificationReplay(Indicator<Num> source) {
        this.source = source;
    }

    /**
     * @return the notification after applying {@code step} to every bar in
     *         {@code [beginIndex, index)} starting from {@code start}
     */
    synchronized Num replay(Num start, int beginIndex, int index, Step step) {
        long[] currentRevisions = currentRevisions();
        int knownLength = length;
        if (currentRevisions == null || !Arrays.equals(currentRevisions, revisions) || beginIndex != begin) {
            knownLength = 0;
            length = 0;
            revisions = currentRevisions;
            begin = beginIndex;
        }
        Num state = start;
        int offset = 0;
        while (true) {
            if (offset < knownLength && sameState(state, states[offset])) {
                // Rejoined: the rest of the known trajectory holds for this start too.
                length = knownLength;
                if (index - beginIndex < knownLength) {
                    return states[index - beginIndex];
                }
                offset = knownLength - 1;
                state = states[offset];
                knownLength = 0;
            } else {
                record(offset, state);
            }
            if (beginIndex + offset == index) {
                return state;
            }
            state = step.next(state, beginIndex + offset);
            offset++;
        }
    }

    synchronized void reset() {
        length = 0;
    }

    // null when a series in the source graph does not track revisions.
    private long[] currentRevisions() {
        if (graphSeries == null) {
            graphSeries = collectGraphSeries();
        }
        long[] current = new long[graphSeries.length];
        for (int i = 0; i < graphSeries.length; i++) {
            long revision = graphSeries[i].getBarHistoryRevision();
            if (revision < 0) {
                return null;
            }
            current[i] = revision;
        }
        return current;
    }

    private BarSeries[] collectGraphSeries() {
        Set<BarSeries> series = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Indicator<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Indicator<?>> pending = new ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty()) {
            Indicator<?> indicator = pending.removeLast();
            if (!visited.add(indicator)) {
                continue;
            }
            if (indicator.getBarSeries() != null) {
                series.add(indicator.getBarSeries());
            }
            pending.addAll(indicator.getDependencies());
        }
        return series.toArray(BarSeries[]::new);
    }

    private void record(int offset, Num state) {
        if (states == null || offset >= states.length) {
            int capacity = states == null ? 16 : states.length;
            while (capacity <= offset) {
                capacity = Math.multiplyExact(capacity, 2);
            }
            states = states == null ? new Num[capacity] : Arrays.copyOf(states, capacity);
        }
        states[offset] = state;
        // Entries past offset may belong to the previous start; only the prefix is
        // known to match this one until the trajectories rejoin.
        length = offset + 1;
    }

    private static boolean sameState(Num left, Num right) {
        return left == right || (left != null && right != null && left.isEqual(right));
    }
}

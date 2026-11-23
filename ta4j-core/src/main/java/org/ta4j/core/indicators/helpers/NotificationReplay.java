/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.Arrays;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Threshold-notification replay shared by the percentage-change indicators.
 *
 * <p>
 * Keeps the trajectory of the last replay: {@code states[m]} is the
 * notification before bar {@code begin + m}. A replay from another start state
 * usually rejoins that trajectory within a few bars, so a full pass costs about
 * one source read per bar instead of rescanning the whole prefix for each one.
 */
final class NotificationReplay {

    @FunctionalInterface
    interface Step {
        Num next(Num notification, int index);
    }

    private Num[] states;
    private int length;
    private int begin;
    private long revision;

    /**
     * @return the notification after applying {@code step} to every bar in
     *         {@code [beginIndex, index)} starting from {@code start}
     */
    synchronized Num replay(BarSeries series, Num start, int beginIndex, int index, Step step) {
        long currentRevision = series.getBarHistoryRevision();
        int knownLength = length;
        if (currentRevision < 0 || currentRevision != revision || beginIndex != begin) {
            knownLength = 0;
            length = 0;
            revision = currentRevision;
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

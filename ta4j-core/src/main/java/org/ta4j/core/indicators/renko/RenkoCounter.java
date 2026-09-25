/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.renko;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Internal helper that calculates Renko brick state for each index.
 *
 * @since 0.19
 */
final class RenkoCounter {

    private final Indicator<Num> priceIndicator;
    private final Num pointSize;
    private final Map<Integer, RenkoState> cache = new HashMap<>();
    private transient BarSeries.BarSeriesChangeSnapshot observedSnapshot;

    RenkoCounter(Indicator<Num> priceIndicator, Num pointSize) {
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.pointSize = Objects.requireNonNull(pointSize, "pointSize must not be null");
    }

    RenkoState stateAt(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        reconcileCache();
        var formingBar = isFormingBar(index);
        if (formingBar) {
            cache.remove(index);
        } else {
            var state = cache.get(index);
            if (state != null) {
                return state;
            }
        }
        var calculated = calculateState(index);
        if (!formingBar) {
            cache.put(index, calculated);
        }
        return calculated;
    }

    /**
     * Evicts stale cached states when the series revision, retained window, or
     * capacity changed since the last observation. Removed bars invalidate the
     * brick ancestry of every cached state, so all entries are cleared and the next
     * computation re-anchors at the retained begin index; otherwise only entries at
     * or after the earliest changed index are stale.
     */
    private void reconcileCache() {
        final BarSeries series = priceIndicator.getBarSeries();
        if (series == null) {
            return;
        }
        final BarSeries.BarSeriesChangeSnapshot snapshot = series
                .getBarSeriesChangeSnapshot(observedSnapshot == null ? -1L : observedSnapshot.revision());
        if (observedSnapshot != null && sameSeriesState(snapshot, observedSnapshot)) {
            return;
        }
        if (observedSnapshot != null && snapshot.removedThroughIndex() != observedSnapshot.removedThroughIndex()) {
            cache.clear();
        } else {
            final int invalidateFrom = snapshot.earliestChangedIndex();
            if (invalidateFrom >= 0) {
                cache.keySet().removeIf(key -> key >= invalidateFrom);
            }
        }
        observedSnapshot = snapshot;
    }

    private static boolean sameSeriesState(BarSeries.BarSeriesChangeSnapshot left,
            BarSeries.BarSeriesChangeSnapshot right) {
        return left.revision() == right.revision() && left.removedThroughIndex() == right.removedThroughIndex()
                && left.maximumBarCount() == right.maximumBarCount() && left.endIndex() == right.endIndex();
    }

    private RenkoState calculateState(int index) {
        final BarSeries series = priceIndicator.getBarSeries();
        final int firstIndex = series == null ? 0 : series.getBeginIndex();
        if (index <= firstIndex) {
            var initial = new RenkoState(priceIndicator.getValue(index), 0, 0);
            if (!isFormingBar(index)) {
                cache.put(index, initial);
            }
            return initial;
        }

        int startIndex = -1;
        RenkoState state = null;
        for (int i = index - 1; i >= firstIndex; i--) {
            state = cache.get(i);
            if (state != null) {
                startIndex = i;
                break;
            }
        }

        if (startIndex < 0) {
            startIndex = firstIndex;
            state = new RenkoState(priceIndicator.getValue(firstIndex), 0, 0);
            if (!isFormingBar(firstIndex)) {
                cache.put(firstIndex, state);
            }
        }

        for (int i = startIndex + 1; i <= index; i++) {
            var price = priceIndicator.getValue(i);
            state = state.advance(price, pointSize);
            if (i != index && !isFormingBar(i)) {
                cache.put(i, state);
            }
        }
        return state;
    }

    static final class RenkoState {

        private final Num lastBrickClose;
        private final int consecutiveUp;
        private final int consecutiveDown;

        private RenkoState(Num lastBrickClose, int consecutiveUp, int consecutiveDown) {
            this.lastBrickClose = lastBrickClose;
            this.consecutiveUp = consecutiveUp;
            this.consecutiveDown = consecutiveDown;
        }

        int getConsecutiveUp() {
            return consecutiveUp;
        }

        int getConsecutiveDown() {
            return consecutiveDown;
        }

        RenkoState advance(Num price, Num pointSize) {
            var lastClose = lastBrickClose;
            var upCount = consecutiveUp;
            var downCount = consecutiveDown;

            var nextUpThreshold = lastClose.plus(pointSize);
            while (price.isGreaterThanOrEqual(nextUpThreshold)) {
                lastClose = nextUpThreshold;
                upCount += 1;
                downCount = 0;
                nextUpThreshold = lastClose.plus(pointSize);
            }

            var nextDownThreshold = lastClose.minus(pointSize);
            while (price.isLessThanOrEqual(nextDownThreshold)) {
                lastClose = nextDownThreshold;
                downCount += 1;
                upCount = 0;
                nextDownThreshold = lastClose.minus(pointSize);
            }

            if (lastClose.equals(lastBrickClose) && upCount == consecutiveUp && downCount == consecutiveDown) {
                return this;
            }

            return new RenkoState(lastClose, upCount, downCount);
        }
    }

    private boolean isFormingBar(int index) {
        var series = priceIndicator.getBarSeries();
        return series != null && index == series.getEndIndex();
    }
}

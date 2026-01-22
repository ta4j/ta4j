/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.renko;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    RenkoCounter(Indicator<Num> priceIndicator, Num pointSize) {
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.pointSize = Objects.requireNonNull(pointSize, "pointSize must not be null");
    }

    RenkoState stateAt(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
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

    private RenkoState calculateState(int index) {
        if (index == 0) {
            var initial = new RenkoState(priceIndicator.getValue(0), 0, 0);
            if (!isFormingBar(0)) {
                cache.put(0, initial);
            }
            return initial;
        }

        int startIndex = -1;
        RenkoState state = null;
        for (int i = index - 1; i >= 0; i--) {
            state = cache.get(i);
            if (state != null) {
                startIndex = i;
                break;
            }
        }

        if (startIndex < 0) {
            startIndex = 0;
            state = new RenkoState(priceIndicator.getValue(0), 0, 0);
            if (!isFormingBar(0)) {
                cache.put(0, state);
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

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Highest value indicator.
 *
 * <p>
 * Returns the highest indicator value from the bar series within the bar count.
 */
public class HighestValueIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public HighestValueIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    public Num calculate(int index) {
        Num currentVal = indicator.getValue(index);

        BarSeries series = indicator.getBarSeries();
        int beginIndex = Math.max(0, series != null ? series.getBeginIndex() : 0);

        if (currentVal.isNaN()) {
            if (barCount == 1) {
                return currentVal;
            }
            int end = Math.max(beginIndex, index - barCount + 1);
            Num highest = currentVal;
            for (int i = index - 1; i >= end; i--) {
                Num val = indicator.getValue(i);
                if (!val.isNaN()) {
                    if (highest.isNaN() || highest.isLessThan(val)) {
                        highest = val;
                    }
                }
            }
            return highest;
        }

        if (index > beginIndex && isCached(index - 1)) {
            Num highestPrev = getValue(index - 1);
            if (!highestPrev.isNaN()) {
                if (currentVal.isGreaterThan(highestPrev)) {
                    return currentVal;
                }
                int oldStartIndex = index - barCount;
                if (oldStartIndex < beginIndex) {
                    return highestPrev;
                }
                Num droppedVal = indicator.getValue(oldStartIndex);
                if (droppedVal.isNaN() || highestPrev.isGreaterThan(droppedVal)) {
                    return highestPrev;
                }
            }
        }

        int end = Math.max(beginIndex, index - barCount + 1);
        Num highest = currentVal;
        for (int i = index - 1; i >= end; i--) {
            Num val = indicator.getValue(i);
            if (!val.isNaN()) {
                if (highest.isLessThan(val)) {
                    highest = val;
                }
            }
        }
        return highest;
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

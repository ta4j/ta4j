/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Lowest value indicator.
 *
 * <p>
 * Returns the lowest indicator value from the bar series within the bar count.
 */
public class LowestValueIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public LowestValueIndicator(Indicator<Num> indicator, int barCount) {
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
            Num lowest = currentVal;
            for (int i = index - 1; i >= end; i--) {
                Num val = indicator.getValue(i);
                if (!val.isNaN()) {
                    if (lowest.isNaN() || lowest.isGreaterThan(val)) {
                        lowest = val;
                    }
                }
            }
            return lowest;
        }

        if (index > beginIndex && isCached(index - 1)) {
            Num lowestPrev = getValue(index - 1);
            if (!lowestPrev.isNaN()) {
                if (currentVal.isLessThan(lowestPrev)) {
                    return currentVal;
                }
                int oldStartIndex = index - barCount;
                if (oldStartIndex < beginIndex) {
                    return lowestPrev;
                }
                Num droppedVal = indicator.getValue(oldStartIndex);
                if (droppedVal.isNaN() || lowestPrev.isLessThan(droppedVal)) {
                    return lowestPrev;
                }
            }
        }

        int end = Math.max(beginIndex, index - barCount + 1);
        Num lowest = currentVal;
        for (int i = index - 1; i >= end; i--) {
            Num val = indicator.getValue(i);
            if (!val.isNaN()) {
                if (lowest.isGreaterThan(val)) {
                    lowest = val;
                }
            }
        }
        return lowest;
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

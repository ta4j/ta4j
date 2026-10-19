/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

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
        if (indicator.getValue(index).isNaN() && barCount != 1) {
            return new HighestValueIndicator(indicator, barCount - 1).getValue(index - 1);
        }
        int end = Math.max(0, index - barCount + 1);
        Num highest = indicator.getValue(index);
        for (int i = index - 1; i >= end; i--) {
            if (highest.isLessThan(indicator.getValue(i))) {
                highest = indicator.getValue(i);
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

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

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
        if (indicator.getValue(index).isNaN() && barCount != 1) {
            return new LowestValueIndicator(indicator, barCount - 1).getValue(index - 1);
        }

        // TODO optimize algorithm, compare previous minimum with current value without
        // looping
        int end = Math.max(0, index - barCount + 1);
        Num lowest = indicator.getValue(index);
        for (int i = index - 1; i >= end; i--) {
            if (lowest.isGreaterThan(indicator.getValue(i))) {
                lowest = indicator.getValue(i);
            }
        }
        return lowest;
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Modified moving average indicator.
 *
 * <p>
 * It is similar to exponential moving average but smooths more slowly. Used in
 * Welles Wilder's indicators like ADX, RSI.
 */
public class MMAIndicator extends AbstractEMAIndicator {

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the MMA time frame
     */
    public MMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator, barCount, 1.0 / barCount);
    }

    /**
     * The EMA-style chain is severed by a head advance: the value at
     * {@code firstRetainedIndex} consumed the removed predecessor, and every value
     * below {@code firstRetainedIndex} plus the declared unstable band still embeds
     * the severed chain. Evicting that band makes the next read rebuild it through
     * the NaN-reset recovery in {@link AbstractEMAIndicator#calculate(int)},
     * matching a fresh calculation against the retained window.
     *
     * @param firstRetainedIndex the first series index that remains available
     * @return {@code firstRetainedIndex + barCount} when no source propagates a
     *         higher floor, saturated at {@link Integer#MAX_VALUE}
     */
    @Override
    protected int minimumCacheableIndexAfterHeadAdvance(int firstRetainedIndex) {
        int propagatedFloor = super.minimumCacheableIndexAfterHeadAdvance(firstRetainedIndex);
        if (propagatedFloor == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        long severedChainFloor = (long) firstRetainedIndex + getCountOfUnstableBars();
        return severedChainFloor >= Integer.MAX_VALUE ? Integer.MAX_VALUE
                : Math.max(propagatedFloor, (int) severedChainFloor);
    }

    @Override
    public int getCountOfUnstableBars() {
        return getBarCount();
    }
}

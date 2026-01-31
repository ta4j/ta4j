/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * ZigZag Pivot Low Indicator.
 * <p>
 * This indicator returns {@code true} at the bar where a new ZigZag swing low
 * is confirmed. A swing low is confirmed when price reverses upward by at least
 * the reversal threshold after reaching a new low during a down-trend.
 * <p>
 * The indicator is useful for:
 * <ul>
 * <li>Detecting when a new swing low is confirmed in real-time</li>
 * <li>Creating rules that trigger on swing low confirmations</li>
 * <li>Identifying potential support levels</li>
 * </ul>
 * <p>
 * The indicator returns {@code false} for all bars except the exact bar where
 * the swing low confirmation occurs. Use {@link RecentZigZagSwingLowIndicator}
 * to get the price value of the most recent swing low at any bar.
 *
 * @see ZigZagStateIndicator
 * @see ZigZagPivotHighIndicator
 * @see RecentZigZagSwingLowIndicator
 * @since 0.20
 */
public class ZigZagPivotLowIndicator extends CachedIndicator<Boolean> {

    private final ZigZagStateIndicator stateIndicator;

    /**
     * Constructs a ZigZagPivotLowIndicator.
     *
     * @param stateIndicator the ZigZagStateIndicator that tracks the ZigZag pattern
     *                       state
     */
    public ZigZagPivotLowIndicator(ZigZagStateIndicator stateIndicator) {
        super(stateIndicator.getBarSeries());
        this.stateIndicator = stateIndicator;
    }

    @Override
    protected Boolean calculate(int index) {
        final BarSeries series = getBarSeries();
        final int beginIndex = series.getBeginIndex();
        if (index <= beginIndex) {
            return Boolean.FALSE;
        }

        ZigZagState prev = stateIndicator.getValue(index - 1);
        ZigZagState curr = stateIndicator.getValue(index);

        return curr.getLastLowIndex() != prev.getLastLowIndex();
    }

    /**
     * Returns the index of the most recent confirmed swing low as of the given
     * index.
     *
     * @param index the bar index to evaluate
     * @return the index of the most recent confirmed swing low, or {@code -1} if no
     *         swing low has been confirmed yet
     */
    public int getLatestSwingLowIndex(int index) {
        return stateIndicator.getValue(index).getLastLowIndex();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

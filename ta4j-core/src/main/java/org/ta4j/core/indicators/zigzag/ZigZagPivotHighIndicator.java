/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.zigzag;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * ZigZag Pivot High Indicator.
 * <p>
 * This indicator returns {@code true} at the bar where a new ZigZag swing high
 * is confirmed. A swing high is confirmed when price reverses downward by at
 * least the reversal threshold after reaching a new high during an up-trend.
 * <p>
 * The indicator is useful for:
 * <ul>
 * <li>Detecting when a new swing high is confirmed in real-time</li>
 * <li>Creating rules that trigger on swing high confirmations</li>
 * <li>Identifying potential resistance levels</li>
 * </ul>
 * <p>
 * The indicator returns {@code false} for all bars except the exact bar where
 * the swing high confirmation occurs. Use
 * {@link RecentZigZagSwingHighIndicator} to get the price value of the most
 * recent swing high at any bar.
 *
 * @see ZigZagStateIndicator
 * @see ZigZagPivotLowIndicator
 * @see RecentZigZagSwingHighIndicator
 * @since 0.20
 */
public class ZigZagPivotHighIndicator extends CachedIndicator<Boolean> {

    private final ZigZagStateIndicator stateIndicator;

    /**
     * Constructs a ZigZagPivotHighIndicator.
     *
     * @param stateIndicator the ZigZagStateIndicator that tracks the ZigZag pattern
     *                       state
     */
    public ZigZagPivotHighIndicator(ZigZagStateIndicator stateIndicator) {
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

        // A new swing high was *just* confirmed if the lastHighIndex changed at this
        // bar.
        return curr.getLastHighIndex() != prev.getLastHighIndex();
    }

    /**
     * Returns the index of the most recent confirmed swing high as of the given
     * index.
     *
     * @param index the bar index to evaluate
     * @return the index of the most recent confirmed swing high, or {@code -1} if
     *         no swing high has been confirmed yet
     */
    public int getLatestSwingHighIndex(int index) {
        return stateIndicator.getValue(index).getLastHighIndex();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

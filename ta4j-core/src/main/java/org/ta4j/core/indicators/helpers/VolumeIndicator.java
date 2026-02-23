/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Volume indicator.
 *
 * <p>
 * Returns the sum of the total traded volumes from the bar series within the
 * bar count.
 *
 * <h2>Algorithm and Complexity</h2>
 * <p>
 * The implementation uses a partial-sums (rolling-window) optimization. For
 * each index {@code i}, the sum is computed as:
 * {@code sum(i) = sum(i-1) + volume(i) - volume(i - barCount)} when
 * {@code i >= barCount}, otherwise {@code sum(i) = sum(i-1) + volume(i)} with
 * {@code sum(-1) = 0}. This reduces per-index work from O(barCount) to O(1),
 * improving performance when {@code barCount} is large or when many indices are
 * evaluated. The base case {@code index == 0} returns {@code volume(0)}
 * directly.
 */
public class VolumeIndicator extends CachedIndicator<Num> {

    private final int barCount;

    /**
     * Constructor with {@code barCount} = 1.
     *
     * @param series the bar series
     */
    public VolumeIndicator(BarSeries series) {
        this(series, 1);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public VolumeIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().getBar(0).getVolume();
        }
        Num prevSum = getValue(index - 1);
        Num currentVolume = getBarSeries().getBar(index).getVolume();
        Num newSum = prevSum.plus(currentVolume);
        if (index >= barCount) {
            Num dropVolume = getBarSeries().getBar(index - barCount).getVolume();
            return newSum.minus(dropVolume);
        }
        return newSum;
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(0, barCount - 1);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
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
 * {@code sum(beginIndex - 1) = 0}. This reduces per-index work from O(barCount)
 * to O(1), improving performance when {@code barCount} is large or when many
 * indices are evaluated. Because the rolling sum depends on {@code sum(i-1)},
 * this indicator extends {@link RecursiveCachedIndicator} so large cold-cache
 * warmups are prefetched iteratively instead of recursing through the entire
 * series. The base case {@code index <= beginIndex} returns
 * {@code volume(index)} directly.
 */
public class VolumeIndicator extends RecursiveCachedIndicator<Num> {

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
        int beginIndex = getBarSeries().getBeginIndex();
        if (index <= beginIndex) {
            return getBarSeries().getBar(index).getVolume();
        }
        Num prevSum = getValue(index - 1);
        Num currentVolume = getBarSeries().getBar(index).getVolume();
        Num newSum = prevSum.plus(currentVolume);
        int dropIndex = index - barCount;
        if (dropIndex >= beginIndex) {
            Num dropVolume = getBarSeries().getBar(dropIndex).getVolume();
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

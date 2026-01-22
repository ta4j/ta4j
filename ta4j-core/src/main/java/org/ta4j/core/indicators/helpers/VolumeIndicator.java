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
        // TODO use partial sums
        int startIndex = Math.max(0, index - barCount + 1);
        Num sumOfVolume = getBarSeries().numFactory().zero();
        for (int i = startIndex; i <= index; i++) {
            sumOfVolume = sumOfVolume.plus(getBarSeries().getBar(i).getVolume());
        }
        return sumOfVolume;
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }
}

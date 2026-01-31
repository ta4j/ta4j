/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Rate of change of volume (ROCVIndicator) indicator (also called "Momentum of
 * Volume").
 *
 * <p>
 * The ROCVIndicator calculation compares the current volume with the volume "n"
 * periods ago.
 */
public class ROCVIndicator extends CachedIndicator<Num> {

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public ROCVIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = getBarSeries().getBar(nIndex).getVolume();
        Num currentValue = getBarSeries().getBar(index).getVolume();
        return currentValue.minus(nPeriodsAgoValue)
                .dividedBy(nPeriodsAgoValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

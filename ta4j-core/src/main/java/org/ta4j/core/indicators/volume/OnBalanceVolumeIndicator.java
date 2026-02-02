/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * On-balance volume indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/o/onbalancevolume.asp">
 *      https://www.investopedia.com/terms/o/onbalancevolume.asp</a>
 */
public class OnBalanceVolumeIndicator extends RecursiveCachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public OnBalanceVolumeIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().zero();
        }
        final Num prevClose = getBarSeries().getBar(index - 1).getClosePrice();
        final Num currentClose = getBarSeries().getBar(index).getClosePrice();

        final Num obvPrev = getValue(index - 1);
        if (prevClose.isGreaterThan(currentClose)) {
            return obvPrev.minus(getBarSeries().getBar(index).getVolume());
        } else if (prevClose.isLessThan(currentClose)) {
            return obvPrev.plus(getBarSeries().getBar(index).getVolume());
        } else {
            return obvPrev;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

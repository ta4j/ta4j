/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Bearish Harami pattern indicator.
 *
 * @see <a href="http://www.investopedia.com/terms/b/bullishharami.asp">
 *      http://www.investopedia.com/terms/b/bullishharami.asp</a>
 */
public class BullishHaramiIndicator extends CachedIndicator<Boolean> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public BullishHaramiIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 1) {
            // Harami is a 2-candle pattern
            return false;
        }
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (prevBar.isBearish() && currBar.isBullish()) {
            final Num prevOpenPrice = prevBar.getOpenPrice();
            final Num prevClosePrice = prevBar.getClosePrice();
            final Num currOpenPrice = currBar.getOpenPrice();
            final Num currClosePrice = currBar.getClosePrice();
            return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
                    && currClosePrice.isLessThan(prevOpenPrice) && currClosePrice.isGreaterThan(prevClosePrice);
        }
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

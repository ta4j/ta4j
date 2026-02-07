/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.donchian;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/***
 * https://www.investopedia.com/terms/d/donchianchannels.asp
 */
public class DonchianChannelLowerIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final LowPriceIndicator lowPrice;
    private final LowestValueIndicator lowestPrice;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public DonchianChannelLowerIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.lowPrice = new LowPriceIndicator(series);
        this.lowestPrice = new LowestValueIndicator(this.lowPrice, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return this.lowestPrice.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return lowestPrice.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "barCount: " + barCount;
    }
}

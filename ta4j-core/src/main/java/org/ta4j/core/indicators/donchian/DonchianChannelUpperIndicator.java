/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.donchian;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * * https://www.investopedia.com/terms/d/donchianchannels.asp
 */
public class DonchianChannelUpperIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final HighPriceIndicator highPrice;
    private final HighestValueIndicator highestPrice;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public DonchianChannelUpperIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.highPrice = new HighPriceIndicator(series);
        this.highestPrice = new HighestValueIndicator(this.highPrice, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return this.highestPrice.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "barCount: " + barCount;
    }
}

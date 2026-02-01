/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.donchian;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * * https://www.investopedia.com/terms/d/donchianchannels.asp
 */
public class DonchianChannelMiddleIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final DonchianChannelLowerIndicator lower;
    private final DonchianChannelUpperIndicator upper;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public DonchianChannelMiddleIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.lower = new DonchianChannelLowerIndicator(series, barCount);
        this.upper = new DonchianChannelUpperIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return (this.lower.getValue(index).plus(this.upper.getValue(index)))
                .dividedBy(getBarSeries().numFactory().two());
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

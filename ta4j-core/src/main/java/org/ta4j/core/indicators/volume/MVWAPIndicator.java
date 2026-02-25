/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The Moving volume weighted average price (MVWAP) Indicator.
 *
 * @see <a href=
 *      "http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp">
 *      http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp</a>
 */
public class MVWAPIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> sma;

    /**
     * Constructor.
     *
     * @param vwap     the vwap
     * @param barCount the time frame
     */
    public MVWAPIndicator(VWAPIndicator vwap, int barCount) {
        super(vwap);
        this.sma = new SMAIndicator(vwap, barCount);
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num value = sma.getValue(index);
        return Num.isNaNOrNull(value) ? NaN.NaN : value;
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return sma.getCountOfUnstableBars();
    }
}

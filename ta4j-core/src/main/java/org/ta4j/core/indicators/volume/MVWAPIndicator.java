/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
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

    @Override
    protected Num calculate(int index) {
        return sma.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}

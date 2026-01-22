/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Close Location Value (CLV) indicator.
 *
 * @see <a href="http://www.investopedia.com/terms/c/close_location_value.asp">
 *      http://www.investopedia.com/terms/c/close_location_value.asp</a>
 */
public class CloseLocationValueIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public CloseLocationValueIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        final Bar bar = getBarSeries().getBar(index);
        final Num low = bar.getLowPrice();
        final Num high = bar.getHighPrice();
        final Num close = bar.getClosePrice();

        final Num diffHighLow = high.minus(low);

        return diffHighLow.isNaN() ? getBarSeries().numFactory().zero()
                : ((close.minus(low)).minus(high.minus(close))).dividedBy(diffHighLow);
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

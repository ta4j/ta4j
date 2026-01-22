/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Average high-low indicator.
 *
 * <p>
 * Returns the median price of a bar using the following formula:
 *
 * <pre>
 * MedianPrice = (highPrice + lowPrice) / 2
 * </pre>
 */
public class MedianPriceIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public MedianPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        final Bar bar = getBarSeries().getBar(index);
        return bar.getHighPrice().plus(bar.getLowPrice()).dividedBy(getBarSeries().numFactory().two());
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Typical price indicator.
 *
 * <p>
 * Returns the typical price of a bar using the following formula:
 *
 * <pre>
 * TypicalPrice = (highPrice + lowPrice + closePrice) / 3
 * </pre>
 */
public class TypicalPriceIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public TypicalPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        final Bar bar = getBarSeries().getBar(index);
        final Num highPrice = bar.getHighPrice();
        final Num lowPrice = bar.getLowPrice();
        final Num closePrice = bar.getClosePrice();
        return highPrice.plus(lowPrice).plus(closePrice).dividedBy(getBarSeries().numFactory().three());
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

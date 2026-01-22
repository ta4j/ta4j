/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Trade count indicator.
 *
 * <p>
 * Returns the number of trades of a bar.
 */
public class TradeCountIndicator extends CachedIndicator<Long> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public TradeCountIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Long calculate(int index) {
        return getBarSeries().getBar(index).getTrades();
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
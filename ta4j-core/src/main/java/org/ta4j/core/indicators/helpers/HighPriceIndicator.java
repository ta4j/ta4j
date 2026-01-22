/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * The high price indicator.
 *
 * <p>
 * Returns the high price of a bar.
 */
public class HighPriceIndicator extends AbstractIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public HighPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        return getBarSeries().getBar(index).getHighPrice();
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

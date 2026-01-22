/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Close price indicator.
 *
 * <p>
 * Returns the close price of a bar.
 */
public class ClosePriceIndicator extends AbstractIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public ClosePriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        return getBarSeries().getBar(index).getClosePrice();
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

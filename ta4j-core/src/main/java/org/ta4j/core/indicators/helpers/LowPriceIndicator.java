/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Low price indicator.
 *
 * <p>
 * Returns the low price of a bar.
 */
public class LowPriceIndicator extends AbstractIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public LowPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        return getBarSeries().getBar(index).getLowPrice();
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

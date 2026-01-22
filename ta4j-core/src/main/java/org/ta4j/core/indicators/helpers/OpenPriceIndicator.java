/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Open price indicator.
 *
 * <p>
 * Returns the open price of a bar.
 */
public class OpenPriceIndicator extends AbstractIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public OpenPriceIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        return getBarSeries().getBar(index).getOpenPrice();
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
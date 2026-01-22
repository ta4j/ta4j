/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Amount indicator.
 *
 * <p>
 * Returns the amount of a bar.
 */
public class AmountIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public AmountIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        return getBarSeries().getBar(index).getAmount();
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Calculates the ratio between the current and the previous close price.
 *
 * <pre>
 * ClosePriceRatio = currentBarClosePrice / previousBarClosePrice
 * </pre>
 */
public class ClosePriceRatioIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public ClosePriceRatioIndicator(BarSeries series) {
        super(series);
    }

    /**
     * Calculates the ratio between the current and previous close prices.
     *
     * @param index the index of the current bar
     * @return the ratio between the close prices
     */
    @Override
    protected Num calculate(int index) {
        // Get the close price of the previous bar
        Num previousBarClosePrice = getBarSeries().getBar(Math.max(0, index - 1)).getClosePrice();

        // Get the close price of the current bar
        Num currentBarClosePrice = getBarSeries().getBar(index).getClosePrice();

        // Calculate the ratio between the close prices
        return currentBarClosePrice.dividedBy(previousBarClosePrice);
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}

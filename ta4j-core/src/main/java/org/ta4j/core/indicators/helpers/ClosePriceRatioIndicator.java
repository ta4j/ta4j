/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator that calculates the ratio between the current and previous close
 * prices.
 * 
 * <pre>
 * PriceVariation = currentBarClosePrice / previousBarClosePrice
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

    /**
     * {@inheritDoc}
     * <p>
     * This indicator is always stable, so it returns 0.
     */
    @Override
    public int getUnstableBars() {
        return 0;
    }
}

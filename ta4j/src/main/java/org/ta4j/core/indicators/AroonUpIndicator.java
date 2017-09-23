/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package org.ta4j.core.indicators;

import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;


/**
 * Aroon up indicator.
 * <p>
 */
public class AroonUpIndicator extends CachedIndicator<Decimal> {

    private final int timeFrame;

    private final HighestValueIndicator highestMaxPriceIndicator;
    private final MaxPriceIndicator maxPriceIndicator;

    public AroonUpIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        maxPriceIndicator = new MaxPriceIndicator(series);

        // + 1 needed for last possible iteration in loop
        highestMaxPriceIndicator = new HighestValueIndicator(maxPriceIndicator, timeFrame+1);
    }

    @Override
    protected Decimal calculate(int index) {
        if (getTimeSeries().getTick(index).getMaxPrice().isNaN())
            return Decimal.NaN;

        // Getting the number of ticks since the highest close price
        int endIndex = Math.max(0,index - timeFrame);
        int nbTicks = 0;
        for (int i = index; i > endIndex; i--) {
            if (maxPriceIndicator.getValue(i).isEqual(highestMaxPriceIndicator.getValue(index))) {
                break;
            }
            nbTicks++;
        }

        return Decimal.valueOf(timeFrame - nbTicks).dividedBy(Decimal.valueOf(timeFrame)).multipliedBy(Decimal.HUNDRED);
    }
}

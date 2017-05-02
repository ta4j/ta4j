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
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;


/**
 * Aroon down indicator.
 * <p>
 */
public class AroonDownIndicator extends CachedIndicator<Decimal> {

    private final int timeFrame;

    private final ClosePriceIndicator closePriceIndicator;

    private final LowestValueIndicator lowestClosePriceIndicator;

    public AroonDownIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        closePriceIndicator = new ClosePriceIndicator(series);
        lowestClosePriceIndicator = new LowestValueIndicator(closePriceIndicator, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        int realTimeFrame = Math.min(timeFrame, index + 1);

        // Getting the number of ticks since the lowest close price
        int endIndex = index - realTimeFrame;
        int nbTicks = 0;
        for (int i = index; i > endIndex; i--) {
            if (closePriceIndicator.getValue(i).isEqual(lowestClosePriceIndicator.getValue(index))) {
                break;
            }
            nbTicks++;
        }
        
        return Decimal.valueOf(realTimeFrame - nbTicks).dividedBy(Decimal.valueOf(realTimeFrame)).multipliedBy(Decimal.HUNDRED);
    }
}

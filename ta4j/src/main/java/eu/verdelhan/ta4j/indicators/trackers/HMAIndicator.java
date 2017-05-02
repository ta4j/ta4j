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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.simple.DifferenceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MultiplierIndicator;

/**
 * Hull moving average (HMA) indicator.
 * <p>
 * @see http://alanhull.com/hull-moving-average
 */
public class HMAIndicator extends CachedIndicator<Decimal> {

    private final int timeFrame;

    private final WMAIndicator sqrtWma;
    
    public HMAIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.timeFrame = timeFrame;
        
        WMAIndicator halfWma = new WMAIndicator(indicator, timeFrame / 2);
        WMAIndicator origWma = new WMAIndicator(indicator, timeFrame);
        
        Indicator indicatorForSqrtWma = new DifferenceIndicator(new MultiplierIndicator(halfWma, Decimal.TWO), origWma);
        sqrtWma = new WMAIndicator(indicatorForSqrtWma, (int) Math.sqrt(timeFrame));
    }

    @Override
    protected Decimal calculate(int index) {
        return sqrtWma.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }

}

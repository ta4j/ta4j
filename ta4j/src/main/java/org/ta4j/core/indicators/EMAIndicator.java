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
import org.ta4j.core.Indicator;

/**
 * Exponential moving average indicator.
 * <p>
 */
public class EMAIndicator extends RecursiveCachedIndicator<Decimal> {

    private final Indicator<Decimal> indicator;

    private final int timeFrame;

    private final Decimal multiplier;

    /**
     * Constructor.
     * @param indicator an indicator
     * @param timeFrame the EMA time frame
     */
    public EMAIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame;
        multiplier = Decimal.TWO.dividedBy(Decimal.valueOf(timeFrame + 1));
    }

    @Override
    protected Decimal calculate(int index) {
        if (index + 1 < timeFrame) {
            // Starting point of the EMA
            return new SMAIndicator(indicator, timeFrame).getValue(index);
        }
        if (index == 0) {
            // If the timeframe is bigger than the indicator's value count
            return indicator.getValue(0);
        }
        Decimal emaPrev = getValue(index - 1);
        return indicator.getValue(index).minus(emaPrev).multipliedBy(multiplier).plus(emaPrev);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}

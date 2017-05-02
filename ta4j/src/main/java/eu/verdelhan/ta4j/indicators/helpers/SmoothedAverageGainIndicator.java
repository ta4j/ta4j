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
package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.RecursiveCachedIndicator;

/**
 * Average gain indicator calculated using smoothing
 * <p>
 */
public class SmoothedAverageGainIndicator extends RecursiveCachedIndicator<Decimal> {

    private final AverageGainIndicator averageGains;
    private final Indicator<Decimal> indicator;

    private final int timeFrame;

    public SmoothedAverageGainIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.averageGains = new AverageGainIndicator(indicator, timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        if(index > timeFrame) {
            return getValue(index - 1)
                .multipliedBy(Decimal.valueOf(timeFrame - 1))
                .plus(calculateGain(index))
                .dividedBy(Decimal.valueOf(timeFrame));
        }
        return averageGains.getValue(index);
    }

    private Decimal calculateGain(int index){
        Decimal gain = indicator.getValue(index).minus(indicator.getValue(index - 1));
        return gain.isPositive() ? gain : Decimal.ZERO;
    }
}

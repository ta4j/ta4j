/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;

/**
 * William's R indicator.
 * <p>
 */
public class WilliamsRIndicator extends CachedIndicator<TADecimal> {

    private final Indicator<? extends TADecimal> indicator;

    private final int timeFrame;

    private MaxPriceIndicator maxPriceIndicator;

    private MinPriceIndicator minPriceIndicator;

    public WilliamsRIndicator(TimeSeries timeSeries, int timeFrame) {
        this(new ClosePriceIndicator(timeSeries), timeFrame, new MaxPriceIndicator(timeSeries), new MinPriceIndicator(
                timeSeries));
    }

    public WilliamsRIndicator(Indicator<? extends TADecimal> indicator, int timeFrame,
            MaxPriceIndicator maxPriceIndicator, MinPriceIndicator minPriceIndicator) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame;
        this.maxPriceIndicator = maxPriceIndicator;
        this.minPriceIndicator = minPriceIndicator;
    }

    @Override
    protected TADecimal calculate(int index) {
        HighestValueIndicator highestHigh = new HighestValueIndicator(maxPriceIndicator, timeFrame);
        LowestValueIndicator lowestMin = new LowestValueIndicator(minPriceIndicator, timeFrame);

        TADecimal highestHighPrice = highestHigh.getValue(index);
        TADecimal lowestLowPrice = lowestMin.getValue(index);

        return ((highestHighPrice.minus(indicator.getValue(index)))
                .dividedBy(highestHighPrice.minus(lowestLowPrice)))
                .multipliedBy(TADecimal.valueOf("-100"));
    }

    @Override
    public String toString() {
        return getClass().getName();
    }
}

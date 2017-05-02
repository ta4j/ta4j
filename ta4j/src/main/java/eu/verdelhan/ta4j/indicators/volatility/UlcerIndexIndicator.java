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
package eu.verdelhan.ta4j.indicators.volatility;

import eu.verdelhan.ta4j.indicators.helpers.*;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Ulcer index indicator.
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index
 * @see https://en.wikipedia.org/wiki/Ulcer_index
 */
public class UlcerIndexIndicator extends CachedIndicator<Decimal> {

    private Indicator<Decimal> indicator;

    private HighestValueIndicator highestValueInd;
    
    private int timeFrame;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param timeFrame the time frame
     */
    public UlcerIndexIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame;
        highestValueInd = new HighestValueIndicator(indicator, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        final int startIndex = Math.max(0, index - timeFrame + 1);
        final int numberOfObservations = index - startIndex + 1;
        Decimal squaredAverage = Decimal.ZERO;
        for (int i = startIndex; i <= index; i++) {
            Decimal currentValue = indicator.getValue(i);
            Decimal highestValue = highestValueInd.getValue(i);
            Decimal percentageDrawdown = currentValue.minus(highestValue).dividedBy(highestValue).multipliedBy(Decimal.HUNDRED);
            squaredAverage = squaredAverage.plus(percentageDrawdown.pow(2));
        }
        squaredAverage = squaredAverage.dividedBy(Decimal.valueOf(numberOfObservations));
        return squaredAverage.sqrt();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}

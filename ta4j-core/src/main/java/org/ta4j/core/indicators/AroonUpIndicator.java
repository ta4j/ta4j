/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;


/**
 * Aroon up indicator.
 * <p></p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon">chart_school:technical_indicators:aroon</a>
 */
public class AroonUpIndicator extends CachedIndicator<Num> {

    private final int barCount;

    private final HighestValueIndicator highestMaxPriceIndicator;
    private final Indicator<Num> maxValueIndicator;
    private final Num hundred;

    /**
     * Constructor.
     * <p>
     * @param series the time series
     * @param maxValueIndicator the indicator for the maximum price (default {@link MaxPriceIndicator})
     * @param barCount the time frame
     */
    public AroonUpIndicator(TimeSeries series, Indicator<Num> maxValueIndicator, int barCount) {
        super(series);
        this.barCount = barCount;
        this.maxValueIndicator = maxValueIndicator;
        this.hundred = numOf(100);
        // + 1 needed for last possible iteration in loop
        highestMaxPriceIndicator = new HighestValueIndicator(maxValueIndicator, barCount+1);
    }

    /**
     * Default Constructor that is using the maximum price
     * <p>
     * @param series the time series
     * @param barCount the time frame
     */
    public AroonUpIndicator(TimeSeries series, int barCount) {
        this(series,new MaxPriceIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (getTimeSeries().getBar(index).getMaxPrice().isNaN())
            return NaN;

        // Getting the number of bars since the highest close price
        int endIndex = Math.max(0,index - barCount);
        int nbBars = 0;
        for (int i = index; i > endIndex; i--) {
            if (maxValueIndicator.getValue(i).isEqual(highestMaxPriceIndicator.getValue(index))) {
                break;
            }
            nbBars++;
        }

        return numOf(barCount - nbBars).dividedBy(numOf(barCount)).multipliedBy(hundred);
    }

    @Override
    public String toString() {
	return getClass().getSimpleName() + " barCount: " + barCount;
    }
}

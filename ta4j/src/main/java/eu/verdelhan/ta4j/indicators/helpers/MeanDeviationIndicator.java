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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Mean deviation indicator.
 * <p>
 * @see http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation
 */
public class MeanDeviationIndicator extends CachedIndicator<Decimal> {

    private Indicator<Decimal> indicator;

    private int timeFrame;

    private SMAIndicator sma;
    
    /**
     * Constructor.
     * @param indicator the indicator
     * @param timeFrame the time frame
     */
    public MeanDeviationIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame;
        sma = new SMAIndicator(indicator, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal absoluteDeviations = Decimal.ZERO;

        final Decimal average = sma.getValue(index);
        final int startIndex = Math.max(0, index - timeFrame + 1);
        final int nbValues = index - startIndex + 1;

        for (int i = startIndex; i <= index; i++) {
            // For each period...
            absoluteDeviations = absoluteDeviations.plus(indicator.getValue(i).minus(average).abs());
        }
        return absoluteDeviations.dividedBy(Decimal.valueOf(nbValues));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}

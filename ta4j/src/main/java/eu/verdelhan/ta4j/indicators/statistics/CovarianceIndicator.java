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
package eu.verdelhan.ta4j.indicators.statistics;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Covariance indicator.
 * <p>
 */
public class CovarianceIndicator extends CachedIndicator<Decimal> {

    private Indicator<Decimal> indicator1;
    
    private Indicator<Decimal> indicator2;

    private int timeFrame;

    private SMAIndicator sma1;
    
    private SMAIndicator sma2;

    /**
     * Constructor.
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param timeFrame the time frame
     */
    public CovarianceIndicator(Indicator<Decimal> indicator1, Indicator<Decimal> indicator2, int timeFrame) {
        super(indicator1);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.timeFrame = timeFrame;
        sma1 = new SMAIndicator(indicator1, timeFrame);
        sma2 = new SMAIndicator(indicator2, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        final int startIndex = Math.max(0, index - timeFrame + 1);
        final int numberOfObservations = index - startIndex + 1;
        Decimal covariance = Decimal.ZERO;
        Decimal average1 = sma1.getValue(index);
        Decimal average2 = sma2.getValue(index);
        for (int i = startIndex; i <= index; i++) {
            Decimal mul = indicator1.getValue(i).minus(average1).multipliedBy(indicator2.getValue(i).minus(average2));
            covariance = covariance.plus(mul);
        }
        covariance = covariance.dividedBy(Decimal.valueOf(numberOfObservations));
        return covariance;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}

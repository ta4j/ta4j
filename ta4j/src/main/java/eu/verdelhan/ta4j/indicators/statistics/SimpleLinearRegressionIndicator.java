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

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Simple linear regression indicator.
 * <p>
 * A moving (i.e. over the time frame) simple linear regression (least squares).
 * y = slope * x + intercept
 * See also: http://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html
 */
public class SimpleLinearRegressionIndicator extends CachedIndicator<Decimal> {

    private Indicator<Decimal> indicator;
    
    private int timeFrame;
    
    private Decimal slope;
    
    private Decimal intercept;
    
    public SimpleLinearRegressionIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        final int startIndex = Math.max(0, index - timeFrame + 1);
        final int endIndex = index;
        if (endIndex - startIndex + 1 < 2) {
            // Not enough observations to compute a regression line
            return Decimal.NaN;
        }
        calculateRegressionLine(startIndex, endIndex);
        return slope.multipliedBy(Decimal.valueOf(index)).plus(intercept);
    }
    
    /**
     * Calculates the regression line.
     * @param startIndex the start index (inclusive) in the time series
     * @param endIndex the end index (inclusive) in the time series
     */
    private void calculateRegressionLine(int startIndex, int endIndex) {
        // First pass: compute xBar and yBar
        Decimal sumX = Decimal.ZERO;
        Decimal sumY = Decimal.ZERO;
        for (int i = startIndex; i <= endIndex; i++) {
            sumX = sumX.plus(Decimal.valueOf(i));
            sumY = sumY.plus(indicator.getValue(i));
        }
        Decimal nbObservations = Decimal.valueOf(endIndex - startIndex + 1);
        Decimal xBar = sumX.dividedBy(nbObservations);
        Decimal yBar = sumY.dividedBy(nbObservations);
        
        // Second pass: compute slope and intercept
        Decimal xxBar = Decimal.ZERO;
        Decimal xyBar = Decimal.ZERO;
        for (int i = startIndex; i <= endIndex; i++) {
            Decimal dX = Decimal.valueOf(i).minus(xBar);
            Decimal dY = indicator.getValue(i).minus(yBar);
            xxBar = xxBar.plus(dX.multipliedBy(dX));
            xyBar = xyBar.plus(dX.multipliedBy(dY));
        }
        
        slope = xyBar.dividedBy(xxBar);
        intercept = yBar.minus(slope.multipliedBy(xBar));
    }
}

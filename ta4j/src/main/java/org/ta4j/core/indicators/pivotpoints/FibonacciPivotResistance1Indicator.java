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
package org.ta4j.core.indicators.pivotpoints;

import org.ta4j.core.Decimal;
import org.ta4j.core.indicators.RecursiveCachedIndicator;

import java.util.List;

/**
 * Fibonacci Pivot Resistance 1 Indicator.
 * <p>
 * @author team172011(Simon-Justus Wimmer), 09.10.2017
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">chart_school: pivotpoints</a>
 */
public class FibonacciPivotResistance1Indicator extends RecursiveCachedIndicator<Decimal> {

    private PivotPointIndicator pivotPointIndicator;
    private final static Decimal FIB_FACTOR = Decimal.valueOf(.382);

    /**Constructor.
     * Calculates the (fibonacci) resistance 1 reversal
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     */
    public FibonacciPivotResistance1Indicator(PivotPointIndicator pivotPointIndicator) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
    }

    @Override
    protected Decimal calculate(int index) {
        List<Integer> ticksOfPreviousPeriod = pivotPointIndicator.getTicksOfPreviousPeriod(index);
        if (ticksOfPreviousPeriod.isEmpty())
            return Decimal.NaN;
        Decimal low = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getMinPrice();
        Decimal high =  getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getMaxPrice();
        for(int i: ticksOfPreviousPeriod){
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
        }
        //Resistance 1 (R1) = P + {.382 * (High  -  Low)}

        return pivotPointIndicator.getValue(index).plus(FIB_FACTOR.multipliedBy(high.minus(low)));
    }
}

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
package org.ta4j.core.indicators.pivotpoints;

import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Fibonacci Reversal Indicator.
 * <p></p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class FibonacciReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final PivotPointIndicator pivotPointIndicator;
    private final FibReversalTyp fibReversalTyp;
    private final Num fibonacciFactor;

    public enum FibReversalTyp{
        SUPPORT,
        RESISTANCE
    }

    /**
     * Standard Fibonacci factors
     */
    public enum FibonacciFactor {
        Factor1(0.382),
        Factor2(0.618),
        Factor3(1);

        private final double factor;

        FibonacciFactor(double factor) {
            this.factor = factor;
        }

        public double getFactor(){
            return this.factor;
        }

    }


    /**Constructor.
     * <p>
     * Calculates a (fibonacci) reversal
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param fibonacciFactor the fibonacci factor for this reversal
     * @param fibReversalTyp the FibonacciReversalIndicator.FibReversalTyp of the reversal (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, double fibonacciFactor, FibReversalTyp fibReversalTyp) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.fibonacciFactor = numOf(fibonacciFactor);
        this.fibReversalTyp = fibReversalTyp;
    }

    /**Constructor.
     * <p>
     * Calculates a (fibonacci) reversal
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param fibonacciFactor the {@link FibonacciFactor} factor for this reversal
     * @param fibReversalTyp the FibonacciReversalIndicator.FibReversalTyp of the reversal (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, FibonacciFactor fibonacciFactor, FibReversalTyp fibReversalTyp){
        this(pivotPointIndicator,fibonacciFactor.getFactor(),fibReversalTyp);
    }

    @Override
    protected Num calculate(int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high =  bar.getMaxPrice();
        Num low = bar.getMinPrice();
        for(int i: barsOfPreviousPeriod){
            high = (getTimeSeries().getBar(i).getMaxPrice()).max(high);
            low = (getTimeSeries().getBar(i).getMinPrice()).min(low);
        }

        if (fibReversalTyp == FibReversalTyp.RESISTANCE) {
            return pivotPointIndicator.getValue(index).plus(fibonacciFactor.multipliedBy(high.minus(low)));
        }
        return pivotPointIndicator.getValue(index).minus(fibonacciFactor.multipliedBy(high.minus(low)));
    }
}

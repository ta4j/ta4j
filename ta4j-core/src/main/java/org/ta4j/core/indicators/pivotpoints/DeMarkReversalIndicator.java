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
 * DeMark Reversal Indicator.
 * <p></p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class DeMarkReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final DeMarkPivotPointIndicator pivotPointIndicator;
    private final DeMarkPivotLevel level;
    private final Num TWO;

    public enum DeMarkPivotLevel{
        RESISTANCE,
        SUPPORT,
    }

    /**
     * Constructor.
     * <p>
     * Calculates the DeMark reversal for the corresponding pivot level
     * @param pivotPointIndicator the {@link DeMarkPivotPointIndicator} for this reversal
     * @param level the {@link DeMarkPivotLevel} for this reversal (RESISTANT, SUPPORT)
     */
    public DeMarkReversalIndicator(DeMarkPivotPointIndicator pivotPointIndicator, DeMarkPivotLevel level) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.level =level;
        TWO = numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        Num x = pivotPointIndicator.getValue(index).multipliedBy(numOf(4));
        Num result;

        if(level == DeMarkPivotLevel.SUPPORT){
            result = calculateSupport(x, index);
        }
        else{
            result = calculateResistance(x, index);
        }

        return result;

    }

    private Num calculateResistance(Num x, int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty()){
            return NaN;
        }
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num low = bar.getMinPrice();
        for(int i: barsOfPreviousPeriod){
            low = getTimeSeries().getBar(i).getMinPrice().min(low);
        }

        return x.dividedBy(TWO).minus(low);
    }

    private Num calculateSupport(Num x, int index){
       List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
       if (barsOfPreviousPeriod.isEmpty()) {
           return NaN;
       }
       Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
       Num high = bar.getMaxPrice();
       for(int i: barsOfPreviousPeriod){
           high = getTimeSeries().getBar(i).getMaxPrice().max(high);
       }

       return x.dividedBy(TWO).minus(high);
   }
}

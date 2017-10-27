/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

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

import org.ta4j.core.Decimal;
import org.ta4j.core.Tick;
import org.ta4j.core.indicators.RecursiveCachedIndicator;

import java.util.List;

/**
 * DeMark Reversal Indicator.
 * <p></p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class DeMarkReversalIndicator extends RecursiveCachedIndicator<Decimal> {

    private final DeMarkPivotPointIndicator pivotPointIndicator;
    private final DeMarkPivotLevel level;

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
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal x = pivotPointIndicator.getValue(index).multipliedBy(Decimal.valueOf(4));
        Decimal result;

        if(level == DeMarkPivotLevel.SUPPORT){
            result = calculateSupport(x, index);
        }
        else{
            result = calculateResistance(x, index);
        }

        return result;

    }

    private Decimal calculateResistance(Decimal x, int index) {
        List<Integer> ticksOfPreviousPeriod = pivotPointIndicator.getTicksOfPreviousPeriod(index);
        if (ticksOfPreviousPeriod.isEmpty()){
            return Decimal.NaN;
        }
        Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
        Decimal low = tick.getMinPrice();
        for(int i: ticksOfPreviousPeriod){
            low = getTimeSeries().getTick(i).getMinPrice().min(low);
        }

        return x.dividedBy(Decimal.TWO).minus(low);
    }

    private Decimal calculateSupport(Decimal x, int index){
       List<Integer> ticksOfPreviousPeriod = pivotPointIndicator.getTicksOfPreviousPeriod(index);
       if (ticksOfPreviousPeriod.isEmpty()) {
           return Decimal.NaN;
       }
       Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
       Decimal high = tick.getMaxPrice();
       for(int i: ticksOfPreviousPeriod){
           high = getTimeSeries().getTick(i).getMaxPrice().max(high);
       }

       return x.dividedBy(Decimal.TWO).minus(high);
   }
}

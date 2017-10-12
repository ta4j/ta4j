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
import org.ta4j.core.Tick;
import org.ta4j.core.indicators.RecursiveCachedIndicator;

import java.util.List;

/**
 * Pivot Reversal Indicator.
 * <p>
 * @author team172011(Simon-Justus Wimmer), 11.10.2017
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">chart_school: pivotpoints</a>
 */
public class StandardReversalIndicator extends RecursiveCachedIndicator<Decimal> {

    private final PivotPointIndicator pivotPointIndicator;
    private final PivotLevel level;

    /**
     * Constructor.
     * <p>
     * Calculates the (standard) reversal for the corresponding pivot level
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param level the {@link PivotLevel} for this reversal
     */
    public StandardReversalIndicator(PivotPointIndicator pivotPointIndicator, PivotLevel level) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.level =level;
    }

    @Override
    protected Decimal calculate(int index) {
        List<Integer> ticksOfPreviousPeriod = pivotPointIndicator.getTicksOfPreviousPeriod(index);
        if (ticksOfPreviousPeriod.isEmpty()) {
            return Decimal.NaN;
        }
        switch (level){
            case RESISTANCE_3:
                return calculateR3(ticksOfPreviousPeriod, index);
            case RESISTANCE_2:
                return calculateR2(ticksOfPreviousPeriod, index);
            case RESISTANCE_1:
                return calculateR1(ticksOfPreviousPeriod, index);
            case SUPPORT_1:
                return  calculateS1(ticksOfPreviousPeriod, index);
            case SUPPORT_2:
                return calculateS2(ticksOfPreviousPeriod, index);
            case SUPPORT_3:
                return calculateS3(ticksOfPreviousPeriod, index);
            default:
                return Decimal.NaN;
        }

    }

    private Decimal calculateR3(List<Integer> ticksOfPreviousPeriod, int index){
        Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
        Decimal low = tick.getMinPrice();
        Decimal high =  tick.getMaxPrice();
        for(int i: ticksOfPreviousPeriod){
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
        }
        return high.plus(Decimal.TWO.multipliedBy((pivotPointIndicator.getValue(index).minus(low))));
    }

    private Decimal calculateR2(List<Integer> ticksOfPreviousPeriod, int index){
        Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
        Decimal low = tick.getMinPrice();
        Decimal high = tick.getMaxPrice();
        for(int i: ticksOfPreviousPeriod){
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
        }
        return pivotPointIndicator.getValue(index).plus((high.minus(low)));
    }

    private Decimal calculateR1(List<Integer> ticksOfPreviousPeriod, int index){
        Decimal low = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getMinPrice();
        for(int i: ticksOfPreviousPeriod){
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
        }
        return Decimal.TWO.multipliedBy(pivotPointIndicator.getValue(index)).minus(low);
    }

    private Decimal calculateS1(List<Integer> ticksOfPreviousPeriod, int index){
        Decimal high =  getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getMaxPrice();
        for(int i: ticksOfPreviousPeriod){
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
        }
        return Decimal.TWO.multipliedBy(pivotPointIndicator.getValue(index)).minus(high);
    }

    private Decimal calculateS2(List<Integer> ticksOfPreviousPeriod, int index){
        Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
        Decimal high =  tick.getMaxPrice();
        Decimal low = tick.getMinPrice();
        for(int i: ticksOfPreviousPeriod){
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
        }
        return pivotPointIndicator.getValue(index).minus((high.minus(low)));
    }

    private Decimal calculateS3(List<Integer> ticksOfPreviousPeriod, int index){
        Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
        Decimal high =  tick.getMaxPrice();
        Decimal low = tick.getMinPrice();
        for(int i: ticksOfPreviousPeriod){
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
        }
        return low.minus(Decimal.TWO.multipliedBy((high.minus(pivotPointIndicator.getValue(index)))));
    }
}

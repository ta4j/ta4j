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
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * DeMark Pivot Point indicator.
 * <p>
 * @author team172011(Simon-Justus Wimmer), 11.10.2017
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">chart_school: pivotpoints</a>
 */
public class DeMarkPivotPointIndicator extends RecursiveCachedIndicator<Decimal> {

    private final TimeLevel timeLevel;

    /**
     * Constructor.
     * <p>
     * Calculates the deMark pivot point based on the time level parameter.
     * @param series the time series with adequate endTime of each tick for the given time level.
     * @param timeLevelId the corresponding time level for pivot calculation:
     *       <ul>
     *          <li>1-, 5-, 10- and 15-minute charts use the prior days high, low and close: <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_DAY (= 1)</li>
     *          <li>30- 60- and 120-minute charts use the prior week's high, low, and close: <b>timeLevelId</b> =  PIVOT_TIME_LEVEL_ID_WEEK (= 2)</li>
     *          <li>Pivot Points for daily charts use the prior month's high, low and close: <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_MONTH (= 3)</li>
     *          <li>Pivot Points for weekly and monthly charts use the prior year's high, low and close: <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_YEAR (= 4)</li>
     *          <li> If you want to use just the last tick data: <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_TICKBASED (= 0)</li>
     *      </ul>
     * The user has to make sure that there are enough previous ticks to calculate correct pivots at the first tick that matters. For example for PIVOT_TIME_LEVEL_ID_MONTH
     * there will be only correct pivot point values (and reversals) after the first complete month
     */
    public DeMarkPivotPointIndicator(TimeSeries series, TimeLevel timeLevelId) {
        super(series);
        this.timeLevel = timeLevelId;
    }

    @Override
    protected Decimal calculate(int index) {
        return calcPivotPoint(getTicksOfPreviousPeriod(index));
    }


	private Decimal calcPivotPoint(List<Integer> ticksOfPreviousPeriod) {
        if (ticksOfPreviousPeriod.isEmpty())
            return Decimal.NaN;
        Tick tick = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0));
		Decimal open = getTimeSeries().getTick(ticksOfPreviousPeriod.get(ticksOfPreviousPeriod.size()-1)).getOpenPrice();
        Decimal close = tick.getClosePrice();
		Decimal high =  tick.getMaxPrice();
		Decimal low = tick.getMinPrice();

        for(int i: ticksOfPreviousPeriod){
            high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
            low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
        }

		Decimal x;

		if (close.isLessThan(open)){
		    x = high.plus(Decimal.TWO.multipliedBy(low)).plus(close);
        }
        else if (close.isGreaterThan(open)) {
            x = Decimal.TWO.multipliedBy(high).plus(low).plus(close);
        }
        else{
		    x = high.plus(low).plus(Decimal.TWO.multipliedBy(close));
        }

		return x.dividedBy(Decimal.valueOf(4));
	}

    /**
     * Calculates the indices of the ticks of the previous period
     * @param index index of the current tick
     * @return list of indices of the ticks of the previous period
     */
	public List<Integer> getTicksOfPreviousPeriod(int index) {
		List<Integer> previousTicks = new ArrayList<>();

        if(timeLevel == TimeLevel.TICKBASED){
            previousTicks.add(Math.max(0, index-1));
            return previousTicks;
        }
        if (index == 0) {
            return previousTicks;
        }


		final Tick currentTick = getTimeSeries().getTick(index);

        // step back while tick-1 in same period (day, week, etc):
		while(index-1 >= getTimeSeries().getBeginIndex() && getPeriod(getTimeSeries().getTick(index-1)) == getPeriod(currentTick)){
			index--;
		}

		// index = last tick in same period, index-1 = first tick in previous period
        long previousPeriod = getPreviousPeriod(currentTick, index-1);
		while(index-1 >= getTimeSeries().getBeginIndex() && getPeriod(getTimeSeries().getTick(index-1)) == previousPeriod){ // while tick-n in previous period
			index--;
			previousTicks.add(index);
		}
		return previousTicks;
	}

	private long getPreviousPeriod(Tick tick, int indexOfPreviousTick) {
		switch (timeLevel) {
            case DAY: // return previous day
                int prevCalendarDay =  tick.getEndTime().minusDays(1).getDayOfYear();
                // skip weekend and holidays:
                while (getTimeSeries().getTick(indexOfPreviousTick).getEndTime().getDayOfYear() != prevCalendarDay && indexOfPreviousTick > 0) {
                    prevCalendarDay--;
                }
                return prevCalendarDay;
            case WEEK: // return previous week
                return tick.getEndTime().minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTH: // return previous month
                return tick.getEndTime().minusMonths(1).getMonthValue();
            default: // return previous year
                return tick.getEndTime().minusYears(1).getYear();
		}
	}

	private long getPeriod(Tick tick) {
        switch (timeLevel) {
            case DAY: // return previous day
                return tick.getEndTime().getDayOfYear();
            case WEEK: // return previous week
                return tick.getEndTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTH: // return previous month
                return tick.getEndTime().getMonthValue();
            default: // return previous year
                return tick.getEndTime().getYear();
        }
	}

}

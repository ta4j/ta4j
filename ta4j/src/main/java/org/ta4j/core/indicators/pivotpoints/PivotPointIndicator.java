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
 * Pivot Point indicator.
 * <p>
 * @author team172011(Simon-Justus Wimmer), 09.10.2017
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">chart_school: pivotpoints</a>
 */
public class PivotPointIndicator extends RecursiveCachedIndicator<Decimal> {

    public static final int PIVOT_TIME_LEVEL_ID_TICKBASED = 0;
    public static final int PIVOT_TIME_LEVEL_ID_DAY = 1; // 1-, 5-, 10- and 15-minute charts use the prior day's high, low and close.
    public static final int PIVOT_TIME_LEVEL_ID_WEEK = 2; // 30- 60- and 120-minute charts use the prior week's high, low, and close.
    public static final int PIVOT_TIME_LEVEL_ID_MONTH = 3; // Pivot Points for daily charts use the prior month's data.
    public static final int PIVOT_TIME_LEVEL_ID_YEAR = 4; // Pivot Points for weekly and monthly charts use the prior year's data

    private int timeLevel;

    /**
     * Constructor.
     * Calculates the pivot reversals based on the time level parameter.
     *
     * <table border="1">
         <tr>
            <td>Bar size</td> <td>Parameter for timeLevelId</td>
         </tr>
         <tr>
            <td> 1-, 5-, 10- and 15-minute charts </td> <td> PIVOT_TIME_LEVEL_ID_DAY (= 1)</td>
         </tr>
         <tr>
            <td> 30- 60- and 120-minute charts use the prior week's high, low, and close </td> <td>  PIVOT_TIME_LEVEL_ID_WEEK (= 2)</td>
         </tr>
         <tr>
            <td> Pivot Points for daily charts use the prior month's data </td> <td> PIVOT_TIME_LEVEL_ID_MONTH (= 3)</td>
         </tr>
         <tr>
            <td> Pivot Points for weekly and monthly charts use the prior year's data </td> <td> PIVOT_TIME_LEVEL_ID_YEAR (= 4)</td>
         </tr>
         <tr>
            <td> Use last tick data </td> <td> PIVOT_TIME_LEVEL_ID_TICKBASED (= 0)</td>
         </tr>
        </table>
     * @param series the time series with adequate endTime of each tick for the given time level.
     */
    public PivotPointIndicator(TimeSeries series, int timeLevelId) {
        super(series);
        if (timeLevelId<0 || timeLevelId>4)
            throw new IllegalArgumentException("The following time level id is not supported:"+timeLevelId);
        this.timeLevel = timeLevelId;
    }


    /**
     * Getter for the time level id
     * @return the the time level id this pivot point indicator was initialized with
     */
    public int getTimeLevel(){
        return this.timeLevel;
    }

    @Override
    protected Decimal calculate(int index) {
        return calcPivotPoint(getTicksOfPreviousPeriod(index), index);
    }


	private Decimal calcPivotPoint(List<Integer> ticksOfPreviousPeriod, int index) {
        if (ticksOfPreviousPeriod.isEmpty())
            return Decimal.NaN;
		Decimal close = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getClosePrice();
		Decimal high =  getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getMaxPrice();
		Decimal low = getTimeSeries().getTick(ticksOfPreviousPeriod.get(0)).getMinPrice();
		for(int i: ticksOfPreviousPeriod){
			high = (getTimeSeries().getTick(i).getMaxPrice()).max(high);
			low = (getTimeSeries().getTick(i).getMinPrice()).min(low);
		}
		return (high.plus(low).plus(close)).dividedBy(Decimal.THREE);
	}

    /**
     * Calculates the indices of the tick in the previous period
     * @param index index of the tick
     * @return List of indices of the ticks of the previous period
     */
	public List<Integer> getTicksOfPreviousPeriod(int index) {
		List<Integer> previousTicks = new ArrayList<>();

        if(timeLevel == PIVOT_TIME_LEVEL_ID_TICKBASED){
            previousTicks.add(Math.max(0, index-1));
            return previousTicks;
        }
        if (index == 0)
            return previousTicks;


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
            case PIVOT_TIME_LEVEL_ID_DAY: // return previous day
                int prevCalendarDay =  tick.getEndTime().minusDays(1).getDayOfYear();
                // skip weekend and holidays:
                while (getTimeSeries().getTick(indexOfPreviousTick).getEndTime().getDayOfYear() != prevCalendarDay && indexOfPreviousTick > 0)
                    prevCalendarDay--;
                return prevCalendarDay;
            case PIVOT_TIME_LEVEL_ID_WEEK: // return previous week
                return tick.getEndTime().minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case PIVOT_TIME_LEVEL_ID_MONTH: // return previous month
                return tick.getEndTime().minusMonths(1).getMonthValue();
            default: // return previous year
                return tick.getEndTime().minusYears(1).getYear();
		}
	}

	private long getPeriod(Tick tick) {
        switch (timeLevel) {
            case PIVOT_TIME_LEVEL_ID_DAY: // return previous day
                int a = tick.getEndTime().getDayOfYear();
                return tick.getEndTime().getDayOfYear();
            case PIVOT_TIME_LEVEL_ID_WEEK: // return previous week
                return tick.getEndTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case PIVOT_TIME_LEVEL_ID_MONTH: // return previous month
                return tick.getEndTime().getMonthValue();
            default: // return previous year
                return tick.getEndTime().getYear();
        }
	}

}

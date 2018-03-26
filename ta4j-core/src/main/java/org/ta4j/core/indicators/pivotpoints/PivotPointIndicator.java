/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.indicators.pivotpoints;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Pivot Point indicator.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">chart_school: pivotpoints</a>
 */
public class PivotPointIndicator extends RecursiveCachedIndicator<Num> {

    private final TimeLevel timeLevel ;

    /**
     * Constructor.
     * <p>
     * Calculates the pivot point based on the time level parameter.
     * @param series the time series with adequate endTime of each bar for the given time level.
     * @param timeLevel the corresponding {@link TimeLevel} for pivot calculation:
     *       <ul>
     *          <li>1-, 5-, 10- and 15-minute charts use the prior days high, low and close: <b>timeLevelId</b> = TimeLevel.DAY</li>
     *          <li>30- 60- and 120-minute charts use the prior week's high, low, and close: <b>timeLevelId</b> =  TimeLevel.WEEK</li>
     *          <li>Pivot Points for daily charts use the prior month's high, low and close: <b>timeLevelId</b> = TimeLevel.MONTH</li>
     *          <li>Pivot Points for weekly and monthly charts use the prior year's high, low and close: <b>timeLevelId</b> = TimeLevel.YEAR (= 4)</li>
     *          <li> If you want to use just the last bar data: <b>timeLevelId</b> = TimeLevel.BARBASED</li>
     *      </ul>
     * The user has to make sure that there are enough previous bars to calculate correct pivots at the first bar that matters. For example for PIVOT_TIME_LEVEL_ID_MONTH
     * there will be only correct pivot point values (and reversals) after the first complete month
     */
    public PivotPointIndicator(TimeSeries series, TimeLevel timeLevel) {
        super(series);
        this.timeLevel = timeLevel;
    }

    @Override
    protected Num calculate(int index) {
        return calcPivotPoint(getBarsOfPreviousPeriod(index));
    }


	private Num calcPivotPoint(List<Integer> barsOfPreviousPeriod) {
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
		Num close = bar.getClosePrice();
		Num high =  bar.getMaxPrice();
		Num low = bar.getMinPrice();
		for(int i: barsOfPreviousPeriod){
			high = (getTimeSeries().getBar(i).getMaxPrice()).max(high);
			low = (getTimeSeries().getBar(i).getMinPrice()).min(low);
		}
		return (high.plus(low).plus(close)).dividedBy(numOf(3));
	}

    /**
     * Calculates the indices of the bars of the previous period
     * @param index index of the current bar
     * @return list of indices of the bars of the previous period
     */
	public List<Integer> getBarsOfPreviousPeriod(int index) {
		List<Integer> previousBars = new ArrayList<>();

        if(timeLevel == TimeLevel.BARBASED){
            previousBars.add(Math.max(0, index-1));
            return previousBars;
        }
        if (index == 0) {
            return previousBars;
        }


		final Bar currentBar = getTimeSeries().getBar(index);

        // step back while bar-1 in same period (day, week, etc):
		while(index-1 >= getTimeSeries().getBeginIndex() && getPeriod(getTimeSeries().getBar(index-1)) == getPeriod(currentBar)){
			index--;
		}

		// index = last bar in same period, index-1 = first bar in previous period
        long previousPeriod = getPreviousPeriod(currentBar, index-1);
		while(index-1 >= getTimeSeries().getBeginIndex() && getPeriod(getTimeSeries().getBar(index-1)) == previousPeriod){ // while bar-n in previous period
			index--;
			previousBars.add(index);
		}
		return previousBars;
	}

	private long getPreviousPeriod(Bar bar, int indexOfPreviousBar) {
		switch (timeLevel) {
            case DAY: // return previous day
                int prevCalendarDay =  bar.getEndTime().minusDays(1).getDayOfYear();
                // skip weekend and holidays:
                while (getTimeSeries().getBar(indexOfPreviousBar).getEndTime().getDayOfYear() != prevCalendarDay && indexOfPreviousBar > 0) {
                    prevCalendarDay--;
                }
                return prevCalendarDay;
            case WEEK: // return previous week
                return bar.getEndTime().minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTH: // return previous month
                return bar.getEndTime().minusMonths(1).getMonthValue();
            default: // return previous year
                return bar.getEndTime().minusYears(1).getYear();
		}
	}

	private long getPeriod(Bar bar) {
        switch (timeLevel) {
            case DAY: // return previous day
                return bar.getEndTime().getDayOfYear();
            case WEEK: // return previous week
                return bar.getEndTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTH: // return previous month
                return bar.getEndTime().getMonthValue();
            default: // return previous year
                return bar.getEndTime().getYear();
        }
	}

}

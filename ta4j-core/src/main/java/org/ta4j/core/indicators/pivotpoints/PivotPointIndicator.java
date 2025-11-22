/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Pivot Point indicator.
 *
 * <p>
 * The {@link java.time.Instant UTC} represents a point in time on the
 * time-line, typically measured in milliseconds. It is independent of time
 * zones, days of the week, or months. However, this rule converts a UTC to a
 * ZonedDateTime in UTC to get the day, week and month in that time zone.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points</a>
 */
public class PivotPointIndicator extends AbstractPivotPointIndicator {

    private final Num three;

    /**
     * Constructor.
     *
     * Calculates the pivot point based on the time level parameter.
     *
     * @param series    the bar series with adequate endTime of each bar for the
     *                  given time level.
     * @param timeLevel the corresponding {@link TimeLevel} for pivot calculation:
     *                  <ul>
     *                  <li>1-, 5-, 10- and 15-minute charts use the prior days
     *                  high, low and close: <b>timeLevelId</b> = TimeLevel.DAY</li>
     *                  <li>30- 60- and 120-minute charts use the prior week's high,
     *                  low, and close: <b>timeLevelId</b> = TimeLevel.WEEK</li>
     *                  <li>Pivot Points for daily charts use the prior month's
     *                  high, low and close: <b>timeLevelId</b> =
     *                  TimeLevel.MONTH</li>
     *                  <li>Pivot Points for weekly and monthly charts use the prior
     *                  year's high, low and close: <b>timeLevelId</b> =
     *                  TimeLevel.YEAR (= 4)</li>
     *                  <li>If you want to use just the last bar data:
     *                  <b>timeLevelId</b> = TimeLevel.BARBASED</li>
     *                  </ul>
     *                  The user has to make sure that there are enough previous
     *                  bars to calculate correct pivots at the first bar that
     *                  matters. For example for PIVOT_TIME_LEVEL_ID_MONTH there
     *                  will be only correct pivot point values (and reversals)
     *                  after the first complete month
     */
    public PivotPointIndicator(BarSeries series, TimeLevel timeLevel) {
        super(series, timeLevel);
        this.three = series.numFactory().numOf(3);
    }

    @Override
    protected Num calcPivotPoint(List<Integer> barsOfPreviousPeriod) {
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        for (int i : barsOfPreviousPeriod) {
            Bar iBar = getBarSeries().getBar(i);
            high = iBar.getHighPrice().max(high);
            low = iBar.getLowPrice().min(low);
        }
        return (high.plus(low).plus(close)).dividedBy(three);
    }

}

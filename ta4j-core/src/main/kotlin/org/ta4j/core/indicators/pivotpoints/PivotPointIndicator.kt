/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.indicators.pivotpoints

import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.RecursiveCachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*
import java.time.temporal.IsoFields
import kotlin.math.max

/**
 * Pivot Point indicator.
 *
 * @see [chart_school:
 * pivotpoints](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points)
 */
class PivotPointIndicator
/**
 * Constructor.
 *
 * Calculates the pivot point based on the time level parameter.
 *
 * @param series    the bar series with adequate endTime of each bar for the
 * given time level.
 * @param timeLevel the corresponding [TimeLevel] for pivot calculation:
 *
 *  * 1-, 5-, 10- and 15-minute charts use the prior days
 * high, low and close: **timeLevelId** = TimeLevel.DAY
 *  * 30- 60- and 120-minute charts use the prior week's high,
 * low, and close: **timeLevelId** = TimeLevel.WEEK
 *  * Pivot Points for daily charts use the prior month's
 * high, low and close: **timeLevelId** =
 * TimeLevel.MONTH
 *  * Pivot Points for weekly and monthly charts use the prior
 * year's high, low and close: **timeLevelId** =
 * TimeLevel.YEAR (= 4)
 *  * If you want to use just the last bar data:
 * **timeLevelId** = TimeLevel.BARBASED
 *
 * The user has to make sure that there are enough previous
 * bars to calculate correct pivots at the first bar that
 * matters. For example for PIVOT_TIME_LEVEL_ID_MONTH there
 * will be only correct pivot point values (and reversals)
 * after the first complete month
 */(series: BarSeries?, private val timeLevel: TimeLevel) : RecursiveCachedIndicator<Num>(series) {
    override fun calculate(index: Int): Num {
        return calcPivotPoint(getBarsOfPreviousPeriod(index))
    }

    private fun calcPivotPoint(barsOfPreviousPeriod: List<Int>): Num {
        if (barsOfPreviousPeriod.isEmpty()) return NaN.NaN
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        val close = bar.closePrice!!
        var high = bar.highPrice!!
        var low = bar.lowPrice!!
        for (i in barsOfPreviousPeriod) {
            high = barSeries.getBar(i).highPrice!!.max(high)
            low = barSeries.getBar(i).lowPrice!!.min(low)
        }
        return (high + low + close) / numOf(3)
    }

    /**
     * Calculates the indices of the bars of the previous period
     *
     * @param _index index of the current bar
     * @return list of indices of the bars of the previous period
     */
    fun getBarsOfPreviousPeriod(_index: Int): List<Int> {
        var index = _index
        val previousBars: MutableList<Int> = ArrayList()
        if (timeLevel == TimeLevel.BARBASED) {
            previousBars.add(max(0, index - 1))
            return previousBars
        }
        if (index == 0) {
            return previousBars
        }
        val currentBar = barSeries!!.getBar(index)

        // step back while bar-1 in same period (day, week, etc):
        while (index - 1 > barSeries.beginIndex
            && getPeriod(barSeries.getBar(index - 1)) == getPeriod(currentBar)
        ) {
            index--
        }

        // index = last bar in same period, index-1 = first bar in previous period
        val previousPeriod = getPreviousPeriod(currentBar, index - 1)
        while (index - 1 >= barSeries.beginIndex
            && getPeriod(barSeries.getBar(index - 1)) == previousPeriod
        ) { // while bar-n in previous period
            index--
            previousBars.add(index)
        }
        return previousBars
    }

    private fun getPreviousPeriod(bar: Bar, indexOfPreviousBar: Int): Long {
        return when (timeLevel) {
            TimeLevel.DAY -> {
                var prevCalendarDay = bar.endTime!!.minusDays(1).dayOfYear
                // skip weekend and holidays:
                while (barSeries!!.getBar(indexOfPreviousBar).endTime!!.dayOfYear != prevCalendarDay && indexOfPreviousBar > 0 && prevCalendarDay >= 0) {
                    prevCalendarDay--
                }
                prevCalendarDay.toLong()
            }
            TimeLevel.WEEK -> bar.endTime!!.minusWeeks(1)[IsoFields.WEEK_OF_WEEK_BASED_YEAR]
            TimeLevel.MONTH -> bar.endTime!!.minusMonths(1).monthValue
            else -> bar.endTime!!.minusYears(1).year
        }.toLong()
    }

    private fun getPeriod(bar: Bar): Long {
        return when (timeLevel) {
            TimeLevel.DAY -> bar.endTime!!.dayOfYear
            TimeLevel.WEEK -> bar.endTime!![IsoFields.WEEK_OF_WEEK_BASED_YEAR]
            TimeLevel.MONTH -> bar.endTime!!.monthValue
            else -> bar.endTime!!.year
        }.toLong()
    }
}
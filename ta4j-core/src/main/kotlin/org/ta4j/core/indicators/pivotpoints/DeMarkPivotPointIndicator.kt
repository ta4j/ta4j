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
 * DeMark Pivot Point indicator.
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points)
 */
class DeMarkPivotPointIndicator(series: BarSeries?, private val timeLevel: TimeLevel) :
    RecursiveCachedIndicator<Num>(series) {
    private val two: Num = numOf(2)

    override fun calculate(index: Int): Num {
        return calcPivotPoint(getBarsOfPreviousPeriod(index))
    }

    private fun calcPivotPoint(barsOfPreviousPeriod: List<Int>): Num {
        if (barsOfPreviousPeriod.isEmpty()) return NaN.NaN
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        val open = barSeries.getBar(barsOfPreviousPeriod[barsOfPreviousPeriod.size - 1]).openPrice
        val close = bar.closePrice!!
        var high = bar.highPrice!!
        var low = bar.lowPrice!!
        for (i in barsOfPreviousPeriod) {
            high = barSeries.getBar(i).highPrice!!.max(high)
            low = barSeries.getBar(i).lowPrice!!.min(low)
        }
        val x = if (close.isLessThan(open)) {
            high.plus(two.times(low)).plus(close)
        } else if (close.isGreaterThan(open)) {
            two.times(high).plus(low).plus(close)
        } else {
            high.plus(low).plus(two.times(close))
        }
        return x.div(numOf(4))
    }

    /**
     * Calculates the indices of the bars of the previous period
     *
     * @param index index of the current bar
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
        while (index - 1 > barSeries.beginIndex
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
                while (barSeries!!.getBar(indexOfPreviousBar).endTime!!.dayOfYear != prevCalendarDay
                    && indexOfPreviousBar > 0
                ) {
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
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

import org.ta4j.core.indicators.RecursiveCachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * Pivot Reversal Indicator.
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points)
 */
/**
 * Constructor.
 *
 * Calculates the (standard) reversal for the corresponding pivot level
 *
 * @param pivotPointIndicator the [PivotPointIndicator] for this reversal
 * @param level               the [PivotLevel] for this reversal
 */
class StandardReversalIndicator(private val pivotPointIndicator: PivotPointIndicator, private val level: PivotLevel) :
    RecursiveCachedIndicator<Num>(
        pivotPointIndicator
    ) {
    override fun calculate(index: Int): Num {
        val barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index)
        return if (barsOfPreviousPeriod.isEmpty()) {
            NaN.NaN
        } else when (level) {
            PivotLevel.RESISTANCE_3 -> calculateR3(barsOfPreviousPeriod, index)
            PivotLevel.RESISTANCE_2 -> calculateR2(barsOfPreviousPeriod, index)
            PivotLevel.RESISTANCE_1 -> calculateR1(barsOfPreviousPeriod, index)
            PivotLevel.SUPPORT_1 -> calculateS1(barsOfPreviousPeriod, index)
            PivotLevel.SUPPORT_2 -> calculateS2(barsOfPreviousPeriod, index)
            PivotLevel.SUPPORT_3 -> calculateS3(barsOfPreviousPeriod, index)
        }
    }

    private fun calculateR3(barsOfPreviousPeriod: List<Int>, index: Int): Num {
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        var low = bar.lowPrice!!
        var high = bar.highPrice!!
        for (i in barsOfPreviousPeriod) {
            low = barSeries.getBar(i).lowPrice!!.min(low)
            high = barSeries.getBar(i).highPrice!!.max(high)
        }
        return high + numOf(2) * (pivotPointIndicator[index] - low)
    }

    private fun calculateR2(barsOfPreviousPeriod: List<Int>, index: Int): Num {
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        var low = bar.lowPrice!!
        var high = bar.highPrice!!
        for (i in barsOfPreviousPeriod) {
            low = barSeries.getBar(i).lowPrice!!.min(low)
            high = barSeries.getBar(i).highPrice!!.max(high)
        }
        return pivotPointIndicator[index] + (high - low)
    }

    private fun calculateR1(barsOfPreviousPeriod: List<Int>, index: Int): Num {
        var low = barSeries!!.getBar(barsOfPreviousPeriod[0]).lowPrice!!
        for (i in barsOfPreviousPeriod) {
            low = barSeries.getBar(i).lowPrice!!.min(low)
        }
        return (numOf(2) * pivotPointIndicator[index]) - low
    }

    private fun calculateS1(barsOfPreviousPeriod: List<Int>, index: Int): Num {
        var high = barSeries!!.getBar(barsOfPreviousPeriod[0]).highPrice!!
        for (i in barsOfPreviousPeriod) {
            high = barSeries.getBar(i).highPrice!!.max(high)
        }
        return numOf(2) * pivotPointIndicator[index] - high
    }

    private fun calculateS2(barsOfPreviousPeriod: List<Int>, index: Int): Num {
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        var high = bar.highPrice!!
        var low = bar.lowPrice!!
        for (i in barsOfPreviousPeriod) {
            high = barSeries.getBar(i).highPrice!!.max(high)
            low = barSeries.getBar(i).lowPrice!!.min(low)
        }
        return pivotPointIndicator[index] - (high - low)
    }

    private fun calculateS3(barsOfPreviousPeriod: List<Int>, index: Int): Num {
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        var high = bar.highPrice!!
        var low = bar.lowPrice!!
        for (i in barsOfPreviousPeriod) {
            high = barSeries.getBar(i).highPrice!!.max(high)
            low = barSeries.getBar(i).lowPrice!!.min(low)
        }
        return low - numOf(2) * (high - pivotPointIndicator[index])
    }
}
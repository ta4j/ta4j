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
package org.ta4j.core.aggregator

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.function.Function

class DurationBarAggregatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private fun getOneDayBars(): List<Bar> {
        val bars: MutableList<Bar> = LinkedList()
        val time = ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault())

        // days 1 - 5
        bars.add(MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(1), 2.0, 3.0, 3.0, 4.0, 5.0, 6.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(2), 3.0, 4.0, 4.0, 5.0, 6.0, 7.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(3), 4.0, 5.0, 6.0, 5.0, 7.0, 8.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(4), 5.0, 9.0, 3.0, 11.0, 2.0, 6.0, 7, numFunction))

        // days 6 - 10
        bars.add(MockBar(time.plusDays(5), 6.0, 10.0, 9.0, 4.0, 8.0, 3.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(6), 3.0, 3.0, 4.0, 95.0, 21.0, 74.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(7), 4.0, 7.0, 63.0, 59.0, 56.0, 89.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(8), 5.0, 93.0, 3.0, 21.0, 29.0, 62.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(9), 6.0, 10.0, 91.0, 43.0, 84.0, 32.0, 7, numFunction))

        // days 11 - 15
        bars.add(MockBar(time.plusDays(10), 4.0, 10.0, 943.0, 49.0, 8.0, 43.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(11), 3.0, 3.0, 43.0, 92.0, 21.0, 784.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(12), 4.0, 74.0, 53.0, 52.0, 56.0, 89.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(13), 5.0, 93.0, 31.0, 221.0, 29.0, 62.0, 7, numFunction))
        bars.add(MockBar(time.plusDays(14), 6.0, 10.0, 991.0, 43.0, 84.0, 32.0, 7, numFunction))

        // day 16
        bars.add(MockBar(time.plusDays(15), 6.0, 108.0, 1991.0, 433.0, 847.0, 322.0, 7, numFunction))
        return bars
    }

    /**
     * Tests if the bars are upscaled correctly from 1day to 5day
     */
    @Test
    fun upscaledTo5DayBars() {
        val barAggregator = DurationBarAggregator(Duration.ofDays(5), true)
        val bars: List<Bar> = barAggregator.aggregate(getOneDayBars())

        // must be 3 bars
        Assert.assertEquals(3, bars.size.toLong())

        // bar 1 must have ohlcv (1, 6, 4, 9, 25)
        val bar1 = bars[0]
        val num1 = bar1.openPrice
        TestUtils.assertNumEquals(num1!!.numOf(1), bar1.openPrice)
        TestUtils.assertNumEquals(num1.numOf(6), bar1.highPrice)
        TestUtils.assertNumEquals(num1.numOf(4), bar1.lowPrice)
        TestUtils.assertNumEquals(num1.numOf(9), bar1.closePrice)
        TestUtils.assertNumEquals(num1.numOf(33), bar1.volume)

        // bar 2 must have ohlcv (6, 91, 4, 10, 260)
        val bar2 = bars[1]
        val num2 = bar2.openPrice
        TestUtils.assertNumEquals(num2!!.numOf(6), bar2.openPrice)
        TestUtils.assertNumEquals(num2.numOf(91), bar2.highPrice)
        TestUtils.assertNumEquals(num2.numOf(4), bar2.lowPrice)
        TestUtils.assertNumEquals(num2.numOf(10), bar2.closePrice)
        TestUtils.assertNumEquals(num2.numOf(260), bar2.volume)

        // bar 3 must have ohlcv (1d, 6d, 4d, 9d, 25)
        val bar3 = bars[2]
        val num3 = bar3.openPrice
        TestUtils.assertNumEquals(num3!!.numOf(4), bar3.openPrice)
        TestUtils.assertNumEquals(num3.numOf(991), bar3.highPrice)
        TestUtils.assertNumEquals(num3.numOf(43), bar3.lowPrice)
        TestUtils.assertNumEquals(num3.numOf(10), bar3.closePrice)
        TestUtils.assertNumEquals(num3.numOf(1010), bar3.volume)
    }

    /**
     * Tests if the bars are upscaled correctly from 1day to 10day
     */
    @Test
    fun upscaledTo10DayBars() {
        val barAggregator = DurationBarAggregator(Duration.ofDays(10), true)
        val bars: List<Bar> = barAggregator.aggregate(getOneDayBars())

        // must be 1 bars
        Assert.assertEquals(1, bars.size.toLong())

        // bar 1 must have ohlcv (1, 91, 4, 10, 293)
        val bar1 = bars[0]
        val num1 = bar1.openPrice
        TestUtils.assertNumEquals(num1!!.numOf(1), bar1.openPrice)
        TestUtils.assertNumEquals(num1.numOf(91), bar1.highPrice)
        TestUtils.assertNumEquals(num1.numOf(4), bar1.lowPrice)
        TestUtils.assertNumEquals(num1.numOf(10), bar1.closePrice)
        TestUtils.assertNumEquals(num1.numOf(293), bar1.volume)
    }

    /**
     * Tests if the bars are upscaled correctly from 1day to 10day, allowed not
     * final bars too
     */
    @Test
    fun upscaledTo10DayBarsNotOnlyFinalBars() {
        val barAggregator = DurationBarAggregator(Duration.ofDays(10), false)
        val bars: List<Bar> = barAggregator.aggregate(getOneDayBars())

        // must be 2 bars
        Assert.assertEquals(2, bars.size.toLong())
    }

    @Test
    fun testWithGapsInSeries() {
        val now = ZonedDateTime.now()
        val barSeries: BarSeries = BaseBarSeries()
        barSeries.addBar(Duration.ofMinutes(1), now.plusMinutes(1), 1, 1, 1, 2, 1)
        barSeries.addBar(Duration.ofMinutes(1), now.plusMinutes(2), 1, 1, 1, 3, 1)
        barSeries.addBar(Duration.ofMinutes(1), now.plusMinutes(60), 1, 1, 1, 1, 1)
        val aggregated2MinSeries = BaseBarSeriesAggregator(
            DurationBarAggregator(Duration.ofMinutes(2), false)
        ).aggregate(barSeries, "")
        val aggregated4MinSeries = BaseBarSeriesAggregator(
            DurationBarAggregator(Duration.ofMinutes(4), false)
        ).aggregate(barSeries, "")
        Assert.assertEquals(2, aggregated2MinSeries.barCount.toLong())
        Assert.assertEquals(2, aggregated4MinSeries.barCount.toLong())
        TestUtils.assertNumEquals(3, aggregated2MinSeries.getBar(0).closePrice)
        TestUtils.assertNumEquals(3, aggregated4MinSeries.getBar(0).closePrice)
        TestUtils.assertNumEquals(2, aggregated2MinSeries.getBar(0).volume)
        TestUtils.assertNumEquals(2, aggregated4MinSeries.getBar(0).volume)
        TestUtils.assertNumEquals(1, aggregated2MinSeries.getBar(1).closePrice)
        TestUtils.assertNumEquals(1, aggregated4MinSeries.getBar(1).closePrice)
        TestUtils.assertNumEquals(1, aggregated2MinSeries.getBar(1).volume)
        TestUtils.assertNumEquals(1, aggregated4MinSeries.getBar(1).volume)
    }
}
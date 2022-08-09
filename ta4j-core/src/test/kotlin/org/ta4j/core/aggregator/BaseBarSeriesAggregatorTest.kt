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
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.function.Function

class BaseBarSeriesAggregatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private val baseBarSeriesAggregator = BaseBarSeriesAggregator(BarAggregatorForTest())
    @Test
    fun testAggregateWithNewName() {
        val bars: MutableList<Bar> = LinkedList()
        val time = ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault())
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7, numFunction)
        val bar1: Bar = MockBar(time.plusDays(1), 2.0, 3.0, 3.0, 4.0, 5.0, 6.0, 7, numFunction)
        val bar2: Bar = MockBar(time.plusDays(2), 3.0, 4.0, 4.0, 5.0, 6.0, 7.0, 7, numFunction)
        bars.add(bar0)
        bars.add(bar1)
        bars.add(bar2)
        val barSeries: BarSeries = BaseBarSeries("name", bars)
        val aggregated = baseBarSeriesAggregator.aggregate(barSeries, "newName")
        Assert.assertEquals("newName", aggregated.name)
        Assert.assertEquals(2, aggregated.barCount.toLong())
        Assert.assertSame(bar0, aggregated.getBar(0))
        Assert.assertSame(bar2, aggregated.getBar(1))
    }

    @Test
    fun testAggregateWithTheSameName() {
        val bars: MutableList<Bar> = LinkedList()
        val time = ZonedDateTime.of(2019, 6, 12, 4, 1, 0, 0, ZoneId.systemDefault())
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7, numFunction)
        val bar1: Bar = MockBar(time.plusDays(1), 2.0, 3.0, 3.0, 4.0, 5.0, 6.0, 7, numFunction)
        val bar2: Bar = MockBar(time.plusDays(2), 3.0, 4.0, 4.0, 5.0, 6.0, 7.0, 7, numFunction)
        bars.add(bar0)
        bars.add(bar1)
        bars.add(bar2)
        val barSeries: BarSeries = BaseBarSeries("name", bars)
        val aggregated = baseBarSeriesAggregator.aggregate(barSeries)
        Assert.assertEquals("name", aggregated!!.name)
        Assert.assertEquals(2, aggregated.barCount.toLong())
        Assert.assertSame(bar0, aggregated.getBar(0))
        Assert.assertSame(bar2, aggregated.getBar(1))
    }

    /**
     * This bar aggregator created only for test purposes is returning first and
     * last bar.
     */
    private class BarAggregatorForTest : BarAggregator {
        override fun aggregate(bars: List<Bar>): MutableList<Bar> {
            //           public List<Bar> aggregate(List<Bar> bars) {
            val aggregated: MutableList<Bar> = ArrayList()
            if (bars.isEmpty()) {
                return aggregated
            }
            val lastBarIndex = bars.size - 1
            aggregated.add(bars[0])
            aggregated.add(bars[lastBarIndex])
            return aggregated
        }
    }
}
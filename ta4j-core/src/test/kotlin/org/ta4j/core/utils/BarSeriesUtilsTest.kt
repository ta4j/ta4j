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
package org.ta4j.core.utils

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBar.Companion.builder
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import org.ta4j.core.utils.BarSeriesUtils.addBars
import org.ta4j.core.utils.BarSeriesUtils.convertBarSeries
import org.ta4j.core.utils.BarSeriesUtils.findMissingBars
import org.ta4j.core.utils.BarSeriesUtils.findOverlappingBars
import org.ta4j.core.utils.BarSeriesUtils.replaceBarIfChanged
import org.ta4j.core.utils.BarSeriesUtils.sortBars
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.function.Function

class BarSeriesUtilsTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private var series: BarSeries? = null
    private var time: ZonedDateTime? = null

    /**
     * Tests if the previous bar is replaced by newBar
     */
    @Test
    fun replaceBarIfChangedTest() {
        val bars: MutableList<Bar> = ArrayList()
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault())
        val time=this.time!!
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 0.0, 7, numFunction)
        val bar1: Bar = MockBar(time.plusDays(1), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val bar2: Bar = MockBar(time.plusDays(2), 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2, numFunction)
        val bar3: Bar = MockBar(time.plusDays(3), 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3, numFunction)
        val bar4: Bar = MockBar(time.plusDays(4), 3.0, 4.0, 4.0, 5.0, 6.0, 4.0, 4, numFunction)
        val bar5: Bar = MockBar(time.plusDays(5), 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5, numFunction)
        val bar6: Bar = MockBar(time.plusDays(6), 6.0, 6.0, 6.0, 6.0, 6.0, 6.0, 6, numFunction)
        bars.add(bar0)
        bars.add(bar1)
        bars.add(bar2)
        bars.add(bar3)
        bars.add(bar4)
        bars.add(bar5)
        bars.add(bar6)
        series = BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("Series Name").withBars(bars).build()
        val newBar3: Bar = MockBar(bar3.endTime, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 33, numFunction)
        val newBar5: Bar = MockBar(bar5.endTime, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 55, numFunction)

        // newBar3 must be replaced with bar3
        val replacedBar3 = replaceBarIfChanged(series!!, newBar3)
        // newBar5 must be replaced with bar5
        val replacedBar5 = replaceBarIfChanged(series!!, newBar5)

        // the replaced bar must be the same as the previous bar
        Assert.assertEquals(bar3, replacedBar3)
        Assert.assertEquals(bar5, replacedBar5)
        Assert.assertNotEquals(bar2, replacedBar3)
        Assert.assertNotEquals(bar6, replacedBar5)

        // the replaced bar must removed from the series
        Assert.assertNotEquals(series!!.getBar(3), replacedBar3)
        Assert.assertNotEquals(series!!.getBar(5), replacedBar5)

        // the new bar must be stored in the series
        Assert.assertEquals(series!!.getBar(3), newBar3)
        Assert.assertEquals(series!!.getBar(5), newBar5)

        // no bar was added
        Assert.assertEquals(7, series!!.barData.size.toLong())
        Assert.assertEquals(7, series!!.barCount.toLong())
    }

    @Test
    fun findMissingBarsTest() {
        val bars: MutableList<Bar> = ArrayList()
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault())
        val time=this.time!!
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 0.0, 7, numFunction)
        val bar1: Bar = MockBar(time.plusDays(1), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val bar4: Bar = MockBar(time.plusDays(4), 3.0, 4.0, 4.0, 5.0, 6.0, 4.0, 4, numFunction)
        val bar5: Bar = MockBar(time.plusDays(5), 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5, numFunction)
        val bar7: Bar = MockBar(time.plusDays(7), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, numFunction)
        val bar8: Bar = builder(Function { obj: Double? -> DoubleNum.Companion.valueOf(obj) }, Double::class.java)
            .timePeriod(Duration.ofDays(1))
            .endTime(time.plusDays(8))
            .openPrice(NaN.NaN)
            .highPrice(NaN.NaN)
            .lowPrice(NaN.NaN)
            .closePrice(NaN.NaN)
            .volume(NaN.NaN)
            .build()
        bars.add(bar0)
        bars.add(bar1)
        bars.add(bar4)
        bars.add(bar5)
        bars.add(bar7)
        bars.add(bar8)
        series = BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("Series Name").withBars(bars).build()

        // return the beginTime of each missing bar
        val missingBars = findMissingBars(series!!, false)

        // there must be 3 missing bars (bar2, bar3, bar6)
        Assert.assertEquals(missingBars[0], time.plusDays(2))
        Assert.assertEquals(missingBars[1], time.plusDays(3))
        Assert.assertEquals(missingBars[2], time.plusDays(6))
        // there must be 1 bar with invalid data (e.g. price, volume)
        Assert.assertEquals(missingBars[3], bar8.endTime)
    }

    @Test
    fun convertBarSeriesTest() {
        val decimalNumFunction = Function<Number?, Num> { obj: Number? -> DecimalNum.Companion.valueOf(obj) }
        val doubleNumFunction = Function<Number?, Num> { obj: Number? -> DoubleNum.Companion.valueOf(obj) }
        val nanNumFunction = Function { obj: Number? -> NaN.valueOf(obj) }
        val bars: MutableList<Bar> = ArrayList()
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault())
        bars.add(MockBar(time!!, 1.0, 2.0, 3.0, 4.0, 5.0, 0.0, 7, decimalNumFunction))
        bars.add(MockBar(time!!.plusDays(1), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, decimalNumFunction))
        bars.add(MockBar(time!!.plusDays(2), 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2, decimalNumFunction))
        val decimalBarSeries: BarSeries = BaseBarSeriesBuilder().withBars(bars)
            .withMaxBarCount(100)
            .withNumTypeOf(DecimalNum::class.java)
            .withName("useDecimalNum")
            .build()

        // convert barSeries with DecimalNum to barSeries with DoubleNum
        val decimalToDoubleSeries = convertBarSeries(decimalBarSeries, doubleNumFunction)

        // convert barSeries with DoubleNum to barSeries with DecimalNum
        val doubleToDecimalSeries = convertBarSeries(
            decimalToDoubleSeries,
            decimalNumFunction
        )

        // convert barSeries with DoubleNum to barSeries with NaNNum
        val doubleToNaNSeries = convertBarSeries(decimalToDoubleSeries, nanNumFunction)
        Assert.assertEquals(decimalBarSeries.firstBar.closePrice!!.javaClass, DecimalNum::class.java)
        Assert.assertEquals(decimalToDoubleSeries.firstBar.closePrice!!.javaClass, DoubleNum::class.java)
        Assert.assertEquals(doubleToDecimalSeries.firstBar.closePrice!!.javaClass, DecimalNum::class.java)
        Assert.assertEquals(doubleToNaNSeries.firstBar.closePrice!!.javaClass, NaN::class.java)
    }

    @Test
    fun findOverlappingBarsTest() {
        val bars: MutableList<Bar> = ArrayList()
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault())
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 0.0, 7, numFunction)
        val bar1: Bar = MockBar(time, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val bar8: Bar = builder(Function { obj: Double? -> DoubleNum.Companion.valueOf(obj) }, Double::class.java)
            .timePeriod(Duration.ofDays(1))
            .endTime(time!!.plusDays(8))
            .openPrice(NaN.NaN)
            .highPrice(NaN.NaN)
            .lowPrice(NaN.NaN)
            .closePrice(NaN.NaN)
            .volume(NaN.NaN)
            .build()
        bars.add(bar0)
        bars.add(bar1)
        bars.add(bar8)
        series = BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("Series Name").withBars(bars).build()
        val overlappingBars = findOverlappingBars(series!!)

        // there must be 1 overlapping bars (bar1)
        Assert.assertEquals(overlappingBars[0].beginTime, bar1.beginTime)
    }

    @Test
    fun addBars() {
        val barSeries: BarSeries = BaseBarSeries("1day", numFunction)
        val bars: MutableList<Bar> = ArrayList()
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault())
        val time=this.time!!
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 0.0, 7, numFunction)
        val bar1: Bar = MockBar(time.plusDays(1), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val bar2: Bar = MockBar(time.plusDays(2), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        bars.add(bar2)
        bars.add(bar0)
        bars.add(bar1)

        // add 3 bars to empty barSeries
        addBars(barSeries, bars)
        Assert.assertEquals(bar0.endTime, barSeries.firstBar.endTime)
        Assert.assertEquals(bar2.endTime, barSeries.lastBar.endTime)
        val bar3: Bar = MockBar(time.plusDays(3), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        bars.add(bar3)

        // add 1 bar to non empty barSeries
        addBars(barSeries, bars)
        Assert.assertEquals(bar3.endTime, barSeries.lastBar.endTime)
    }

    @Test
    fun sortBars() {
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault())
        val time=this.time!!
        val bar0: Bar = MockBar(time, 1.0, 2.0, 3.0, 4.0, 5.0, 0.0, 7, numFunction)
        val bar1: Bar = MockBar(time.plusDays(1), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val bar2: Bar = MockBar(time.plusDays(2), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val bar3: Bar = MockBar(time.plusDays(3), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1, numFunction)
        val sortedBars: MutableList<Bar> = ArrayList()
        sortedBars.add(bar0)
        sortedBars.add(bar1)
        sortedBars.add(bar2)
        sortedBars.add(bar3)
        sortBars(sortedBars)
        Assert.assertEquals(bar0.endTime, sortedBars[0].endTime)
        Assert.assertEquals(bar1.endTime, sortedBars[1].endTime)
        Assert.assertEquals(bar2.endTime, sortedBars[2].endTime)
        Assert.assertEquals(bar3.endTime, sortedBars[3].endTime)
        val unsortedBars: MutableList<Bar> = ArrayList()
        unsortedBars.add(bar3)
        unsortedBars.add(bar2)
        unsortedBars.add(bar1)
        unsortedBars.add(bar0)
        sortBars(unsortedBars)
        Assert.assertEquals(bar0.endTime, unsortedBars[0].endTime)
        Assert.assertEquals(bar1.endTime, unsortedBars[1].endTime)
        Assert.assertEquals(bar2.endTime, unsortedBars[2].endTime)
        Assert.assertEquals(bar3.endTime, unsortedBars[3].endTime)
        val unsortedBars2: MutableList<Bar> = ArrayList()
        unsortedBars2.add(bar2)
        unsortedBars2.add(bar1)
        unsortedBars2.add(bar3)
        unsortedBars2.add(bar0)
        sortBars(unsortedBars2)
        Assert.assertEquals(bar0.endTime, unsortedBars2[0].endTime)
        Assert.assertEquals(bar1.endTime, unsortedBars2[1].endTime)
        Assert.assertEquals(bar2.endTime, unsortedBars2[2].endTime)
        Assert.assertEquals(bar3.endTime, unsortedBars2[3].endTime)
        unsortedBars2.shuffle()
        sortBars(unsortedBars2)
        Assert.assertEquals(bar0.endTime, unsortedBars2[0].endTime)
        Assert.assertEquals(bar1.endTime, unsortedBars2[1].endTime)
        Assert.assertEquals(bar2.endTime, unsortedBars2[2].endTime)
        Assert.assertEquals(bar3.endTime, unsortedBars2[3].endTime)
    }
}
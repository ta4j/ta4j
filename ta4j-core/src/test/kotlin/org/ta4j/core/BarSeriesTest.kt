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
package org.ta4j.core

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.HighPriceIndicator
import org.ta4j.core.indicators.helpers.LowPriceIndicator
import org.ta4j.core.indicators.helpers.PreviousValueIndicator
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import org.ta4j.core.rules.FixedRule
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.function.Function
import java.util.stream.IntStream

class BarSeriesTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private var defaultSeries: BarSeries? = null
    private var subSeries: BarSeries? = null
    private var emptySeries: BarSeries? = null
    private var bars: MutableList<Bar> = LinkedList()
    private var defaultName: String? = null
    @Before
    fun setUp() {
        bars = LinkedList()
        bars.add(MockBar(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1.0, numFunction))
        bars.add(MockBar(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2.0, numFunction))
        bars.add(MockBar(ZonedDateTime.of(2014, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()), 3.0, numFunction))
        bars.add(MockBar(ZonedDateTime.of(2014, 6, 20, 0, 0, 0, 0, ZoneId.systemDefault()), 4.0, numFunction))
        bars.add(MockBar(ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault()), 5.0, numFunction))
        bars.add(MockBar(ZonedDateTime.of(2014, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault()), 6.0, numFunction))
        defaultName = "Series Name"
        defaultSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction)
            .withName(defaultName!!)
            .withBars(bars)
            .build()
        subSeries = defaultSeries!!.getSubSeries(2, 5)
        emptySeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        val strategy: Strategy = BaseStrategy(FixedRule(0, 2, 3, 6), FixedRule(1, 4, 7, 8))
        strategy.unstablePeriod = 2 // Strategy would need a real test class
    }

    /**
     * Tests if the addBar(bar, boolean) function works correct.
     */
    @Test
    fun replaceBarTest() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        series.addBar(MockBar(ZonedDateTime.now(ZoneId.systemDefault()), 1.0, numFunction), true)
        Assert.assertEquals(1, series.barCount.toLong())
        TestUtils.assertNumEquals(series.lastBar.closePrice, series.numOf(1))
        series.addBar(MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(1), 2.0, numFunction), false)
        series.addBar(MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(2), 3.0, numFunction), false)
        Assert.assertEquals(3, series.barCount.toLong())
        TestUtils.assertNumEquals(series.lastBar.closePrice, series.numOf(3))
        series.addBar(MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(3), 4.0, numFunction), true)
        series.addBar(MockBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(4), 5.0, numFunction), true)
        Assert.assertEquals(3, series.barCount.toLong())
        TestUtils.assertNumEquals(series.lastBar.closePrice, series.numOf(5))
    }

    @Test
    fun getEndGetBeginGetBarCountIsEmptyTest() {

        // Default series
        Assert.assertEquals(0, defaultSeries!!.beginIndex.toLong())
        Assert.assertEquals((bars.size - 1).toLong(), defaultSeries!!.endIndex.toLong())
        Assert.assertEquals(bars.size.toLong(), defaultSeries!!.barCount.toLong())
        Assert.assertFalse(defaultSeries!!.isEmpty)
        // Constrained series
        Assert.assertEquals(0, subSeries!!.beginIndex.toLong())
        Assert.assertEquals(2, subSeries!!.endIndex.toLong())
        Assert.assertEquals(3, subSeries!!.barCount.toLong())
        Assert.assertFalse(subSeries!!.isEmpty)
        // Empty series
        Assert.assertEquals(-1, emptySeries!!.beginIndex.toLong())
        Assert.assertEquals(-1, emptySeries!!.endIndex.toLong())
        Assert.assertEquals(0, emptySeries!!.barCount.toLong())
        Assert.assertTrue(emptySeries!!.isEmpty)
    }

    @Test
    fun getBarDataTest() {
        // Default series
        Assert.assertEquals(bars, defaultSeries!!.barData)
        // Constrained series
        Assert.assertNotEquals(bars, subSeries!!.barData)
        // Empty series
        Assert.assertEquals(0, emptySeries!!.barData.size.toLong())
    }

    @Test
    fun getSeriesPeriodDescriptionTest() {
        // Default series
        Assert.assertTrue(
            defaultSeries!!.seriesPeriodDescription.endsWith(bars[defaultSeries!!.endIndex].endTime!!.format(DateTimeFormatter.ISO_DATE_TIME))
        )
        Assert.assertTrue(
            defaultSeries!!.seriesPeriodDescription.startsWith(
                    bars[defaultSeries!!.beginIndex].endTime!!.format(DateTimeFormatter.ISO_DATE_TIME)
                )
        )
        // Constrained series
        Assert.assertTrue(
            subSeries!!.seriesPeriodDescription.endsWith(bars[4].endTime!!.format(DateTimeFormatter.ISO_DATE_TIME))
        )
        Assert.assertTrue(
            subSeries!!.seriesPeriodDescription.startsWith(bars[2].endTime!!.format(DateTimeFormatter.ISO_DATE_TIME))
        )
        // Empty series
        Assert.assertEquals("", emptySeries!!.seriesPeriodDescription)
    }

    @Test
    fun getNameTest() {
        Assert.assertEquals(defaultName, defaultSeries!!.name)
        Assert.assertEquals(defaultName, subSeries!!.name)
    }

    @Test
    fun getBarWithRemovedIndexOnMovingSeriesShouldReturnFirstRemainingBarTest() {
        val bar = defaultSeries!!.getBar(4)
        defaultSeries!!.setMaximumBarCount(2)
        Assert.assertSame(bar, defaultSeries!!.getBar(0))
        Assert.assertSame(bar, defaultSeries!!.getBar(1))
        Assert.assertSame(bar, defaultSeries!!.getBar(2))
        Assert.assertSame(bar, defaultSeries!!.getBar(3))
        Assert.assertSame(bar, defaultSeries!!.getBar(4))
        Assert.assertNotSame(bar, defaultSeries!!.getBar(5))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getBarOnMovingAndEmptySeriesShouldThrowExceptionTest() {
        defaultSeries!!.setMaximumBarCount(2)
        bars.clear() // Should not be used like this
        defaultSeries!!.getBar(1)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getBarWithNegativeIndexShouldThrowExceptionTest() {
        defaultSeries!!.getBar(-1)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun getBarWithIndexGreaterThanBarCountShouldThrowExceptionTest() {
        defaultSeries!!.getBar(10)
    }

    @Test
    fun getBarOnMovingSeriesTest() {
        val bar = defaultSeries!!.getBar(4)
        defaultSeries!!.setMaximumBarCount(2)
        Assert.assertEquals(bar, defaultSeries!!.getBar(4))
    }

    @Test
    fun subSeriesCreationTest() {
        var subSeries = defaultSeries!!.getSubSeries(2, 5)
        Assert.assertEquals(3, subSeries.barCount.toLong())
        Assert.assertEquals(defaultSeries!!.name, subSeries.name)
        Assert.assertEquals(0, subSeries.beginIndex.toLong())
        Assert.assertEquals(defaultSeries!!.beginIndex.toLong(), subSeries.beginIndex.toLong())
        Assert.assertEquals(2, subSeries.endIndex.toLong())
        Assert.assertNotEquals(defaultSeries!!.endIndex.toLong(), subSeries.endIndex.toLong())
        Assert.assertEquals(3, subSeries.barCount.toLong())
        subSeries = defaultSeries!!.getSubSeries(0, 1000)
        Assert.assertEquals(0, subSeries.beginIndex.toLong())
        Assert.assertEquals(defaultSeries!!.barCount.toLong(), subSeries.barCount.toLong())
        Assert.assertEquals(defaultSeries!!.endIndex.toLong(), subSeries.endIndex.toLong())
    }

    @Test(expected = IllegalArgumentException::class)
    fun subSeriesCreationWithNegativeIndexTest() {
        defaultSeries!!.getSubSeries(-1000, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun subSeriesWithWrongArgumentsTest() {
        defaultSeries!!.getSubSeries(10, 9)
    }

    @Test
    fun maximumBarCountOnConstrainedSeriesShouldNotThrowExceptionTest() {
        try {
            subSeries!!.setMaximumBarCount(10)
        } catch (e: Exception) {
            Assert.fail("setMaximumBarCount onConstrained series should not throw Exception")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeMaximumBarCountShouldThrowExceptionTest() {
        defaultSeries!!.setMaximumBarCount(-1)
    }

    @Test
    fun setMaximumBarCountTest() {
        // Before
        Assert.assertEquals(0, defaultSeries!!.beginIndex.toLong())
        Assert.assertEquals((bars.size - 1).toLong(), defaultSeries!!.endIndex.toLong())
        Assert.assertEquals(bars.size.toLong(), defaultSeries!!.barCount.toLong())
        defaultSeries!!.setMaximumBarCount(3)

        // After
        Assert.assertEquals(0, defaultSeries!!.beginIndex.toLong())
        Assert.assertEquals(5, defaultSeries!!.endIndex.toLong())
        Assert.assertEquals(3, defaultSeries!!.barCount.toLong())
    }

    @Test(expected = NullPointerException::class)
    fun addNullBarShouldThrowExceptionTest() {
        val a:Bar?=null
        defaultSeries!!.addBar(a!!)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addBarWithEndTimePriorToSeriesEndTimeShouldThrowExceptionTest() {
        defaultSeries!!.addBar(
            MockBar(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()), 99.0, numFunction)
        )
    }

    @Test
    fun addBarTest() {
        defaultSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        val bar1: Bar = MockBar(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1.0, numFunction)
        val bar2: Bar = MockBar(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2.0, numFunction)
        Assert.assertEquals(0, defaultSeries!!.barCount.toLong())
        Assert.assertEquals(-1, defaultSeries!!.beginIndex.toLong())
        Assert.assertEquals(-1, defaultSeries!!.endIndex.toLong())
        defaultSeries!!.addBar(bar1)
        Assert.assertEquals(1, defaultSeries!!.barCount.toLong())
        Assert.assertEquals(0, defaultSeries!!.beginIndex.toLong())
        Assert.assertEquals(0, defaultSeries!!.endIndex.toLong())
        defaultSeries!!.addBar(bar2)
        Assert.assertEquals(2, defaultSeries!!.barCount.toLong())
        Assert.assertEquals(0, defaultSeries!!.beginIndex.toLong())
        Assert.assertEquals(1, defaultSeries!!.endIndex.toLong())
    }

    @Test
    fun addPriceTest() {
        val cp = ClosePriceIndicator(defaultSeries)
        val mxPrice = HighPriceIndicator(defaultSeries)
        val mnPrice = LowPriceIndicator(defaultSeries)
        val prevValue = PreviousValueIndicator(cp, 1)
        val adding1 = numOf(100)
        val prevClose = defaultSeries!!.getBar(defaultSeries!!.endIndex - 1).closePrice
        val currentMin = mnPrice.getValue(defaultSeries!!.endIndex)
        val currentClose = cp.getValue(defaultSeries!!.endIndex)
        TestUtils.assertNumEquals(currentClose, defaultSeries!!.lastBar.closePrice)
        defaultSeries!!.addPrice(adding1)
        TestUtils.assertNumEquals(adding1, cp.getValue(defaultSeries!!.endIndex)) // adding1 is new close
        TestUtils.assertNumEquals(adding1, mxPrice.getValue(defaultSeries!!.endIndex)) // adding1 also new max
        TestUtils.assertNumEquals(currentMin, mnPrice.getValue(defaultSeries!!.endIndex)) // min stays same
        TestUtils.assertNumEquals(prevClose, prevValue.getValue(defaultSeries!!.endIndex)) // previous close stays
        val adding2 = numOf(0)
        defaultSeries!!.addPrice(adding2)
        TestUtils.assertNumEquals(adding2, cp.getValue(defaultSeries!!.endIndex)) // adding2 is new close
        TestUtils.assertNumEquals(adding1, mxPrice.getValue(defaultSeries!!.endIndex)) // max stays 100
        TestUtils.assertNumEquals(adding2, mnPrice.getValue(defaultSeries!!.endIndex)) // min is new adding2
        TestUtils.assertNumEquals(prevClose, prevValue.getValue(defaultSeries!!.endIndex)) // previous close stays
    }

    /**
     * Tests if the [BaseBarSeries.addTrade] method works
     * correct.
     */
    @Test
    fun addTradeTest() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        series.addBar(MockBar(ZonedDateTime.now(ZoneId.systemDefault()), 1.0, numFunction))
        series.addTrade(200, 11.5)
        TestUtils.assertNumEquals(series.numOf(200), series.lastBar.volume)
        TestUtils.assertNumEquals(series.numOf(11.5), series.lastBar.closePrice)
        series.addTrade(BigDecimal.valueOf(200), BigDecimal.valueOf(100))
        TestUtils.assertNumEquals(series.numOf(400), series.lastBar.volume)
        TestUtils.assertNumEquals(series.numOf(100), series.lastBar.closePrice)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongBarTypeDoubleTest() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(DoubleNum::class.java).build()
        series.addBar(
            BaseBar(
                Duration.ofDays(1),
                ZonedDateTime.now(),
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1
            ) { obj: Number? -> DecimalNum.Companion.valueOf(obj) }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongBarTypeBigDecimalTest() {
        val series: BarSeries =
            BaseBarSeriesBuilder().withNumTypeOf { obj: Number? -> DecimalNum.Companion.valueOf(obj) }
                .build()
        series.addBar(
            BaseBar(
                Duration.ofDays(1),
                ZonedDateTime.now(),
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1
            ) { obj: Number? -> DoubleNum.Companion.valueOf(obj) }
        )
    }

    @Test
    fun subSeriesOfMaxBarCountSeriesTest() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction)
            .withName("Series with maxBar count")
            .withMaxBarCount(20)
            .build()
        val timespan = 5
        IntStream.range(0, 100).forEach { i: Int ->
            series.addBar(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(i.toLong()), 5, 7, 1, 5, i)
            val startIndex = Math.max(0, series.endIndex - timespan + 1)
            val endIndex = i + 1
            val subSeries = series.getSubSeries(startIndex, endIndex)
            Assert.assertEquals(subSeries.barCount.toLong(), (endIndex - startIndex).toLong())
            val subSeriesLastBar = subSeries.lastBar
            val seriesLastBar = series.lastBar
            Assert.assertEquals(subSeriesLastBar.volume, seriesLastBar.volume)
        }
    }
}
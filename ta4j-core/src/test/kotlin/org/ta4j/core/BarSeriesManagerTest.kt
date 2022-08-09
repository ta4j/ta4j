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
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import org.ta4j.core.rules.FixedRule
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Function

class BarSeriesManagerTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private var seriesForRun: BarSeries? = null
    private var manager: BarSeriesManager? = null
    private var strategy: Strategy? = null
    private val hundred = numOf(100)
    @Before
    fun setUp() {
        val dtf = DateTimeFormatter.ISO_ZONED_DATE_TIME
        seriesForRun = MockBarSeries(
            numFunction, doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0), arrayOf(
                ZonedDateTime.parse("2013-01-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2013-08-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2013-10-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2013-12-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2014-02-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2015-01-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2015-08-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2015-10-01T00:00:00-05:00", dtf),
                ZonedDateTime.parse("2015-12-01T00:00:00-05:00", dtf)
            )
        )
        manager = BarSeriesManager(seriesForRun!!)
        strategy = BaseStrategy(FixedRule(0, 2, 3, 6), FixedRule(1, 4, 7, 8))
        strategy?.unstablePeriod = 2 // Strategy would need a real test class
    }

    @Test
    fun runOnWholeSeries() {
        val series: BarSeries = MockBarSeries(numFunction, 20.0, 40.0, 60.0, 10.0, 30.0, 50.0, 0.0, 20.0, 40.0)
        manager = BarSeriesManager(series)
        val allPositions = manager!!.run(strategy!!).positions
        Assert.assertEquals(2, allPositions.size.toLong())
    }

    @Test
    fun runOnWholeSeriesWithAmount() {
        val series: BarSeries = MockBarSeries(numFunction, 20.0, 40.0, 60.0, 10.0, 30.0, 50.0, 0.0, 20.0, 40.0)
        manager = BarSeriesManager(series)
        val allPositions = manager!!.run(strategy!!, TradeType.BUY, hundred).positions
        Assert.assertEquals(2, allPositions.size.toLong())
        Assert.assertEquals(hundred, allPositions[0].entry!!.amount)
        Assert.assertEquals(hundred, allPositions[1].entry!!.amount)
    }

    @Test
    fun runOnSeries() {
        val positions = manager!!.run(strategy!!).positions
        Assert.assertEquals(2, positions.size.toLong())
        Assert.assertEquals(buyAt(2, seriesForRun!!.getBar(2).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(4, seriesForRun!!.getBar(4).closePrice, numOf(1)), positions[0].exit)
        Assert.assertEquals(buyAt(6, seriesForRun!!.getBar(6).closePrice, numOf(1)), positions[1].entry)
        Assert.assertEquals(sellAt(7, seriesForRun!!.getBar(7).closePrice, numOf(1)), positions[1].exit)
    }

    @Test
    fun runWithOpenEntryBuyLeft() {
        val aStrategy: Strategy = BaseStrategy(FixedRule(1), FixedRule(3))
        val positions = manager!!.run(aStrategy, 0, 3).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(1, seriesForRun!!.getBar(1).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(3, seriesForRun!!.getBar(3).closePrice, numOf(1)), positions[0].exit)
    }

    @Test
    fun runWithOpenEntrySellLeft() {
        val aStrategy: Strategy = BaseStrategy(FixedRule(1), FixedRule(3))
        val positions = manager!!.run(aStrategy, TradeType.SELL, 0, 3).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(sellAt(1, seriesForRun!!.getBar(1).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(buyAt(3, seriesForRun!!.getBar(3).closePrice, numOf(1)), positions[0].exit)
    }

    @Test
    fun runBetweenIndexes() {
        var positions = manager!!.run(strategy!!, 0, 3).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(2, seriesForRun!!.getBar(2).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(4, seriesForRun!!.getBar(4).closePrice, numOf(1)), positions[0].exit)
        positions = manager!!.run(strategy!!, 4, 4).positions
        Assert.assertTrue(positions.isEmpty())
        positions = manager!!.run(strategy!!, 5, 8).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(6, seriesForRun!!.getBar(6).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(7, seriesForRun!!.getBar(7).closePrice, numOf(1)), positions[0].exit)
    }

    @Test
    fun runOnSeriesSlices() {
        val dateTime = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
        val series: BarSeries = MockBarSeries(
            numFunction, doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0), arrayOf(
                dateTime.withYear(2000), dateTime.withYear(2000), dateTime.withYear(2001),
                dateTime.withYear(2001), dateTime.withYear(2002), dateTime.withYear(2002),
                dateTime.withYear(2002), dateTime.withYear(2003), dateTime.withYear(2004),
                dateTime.withYear(2005)
            )
        )
        manager = BarSeriesManager(series)
        val aStrategy: Strategy = BaseStrategy(FixedRule(0, 3, 5, 7), FixedRule(2, 4, 6, 9))
        var positions = manager!!.run(aStrategy, 0, 1).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(0, series.getBar(0).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(2, series.getBar(2).closePrice, numOf(1)), positions[0].exit)
        positions = manager!!.run(aStrategy, 2, 3).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(3, series.getBar(3).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(4, series.getBar(4).closePrice, numOf(1)), positions[0].exit)
        positions = manager!!.run(aStrategy, 4, 6).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(5, series.getBar(5).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(6, series.getBar(6).closePrice, numOf(1)), positions[0].exit)
        positions = manager!!.run(aStrategy, 7, 7).positions
        Assert.assertEquals(1, positions.size.toLong())
        Assert.assertEquals(buyAt(7, series.getBar(7).closePrice, numOf(1)), positions[0].entry)
        Assert.assertEquals(sellAt(9, series.getBar(9).closePrice, numOf(1)), positions[0].exit)
        positions = manager!!.run(aStrategy, 8, 8).positions
        Assert.assertTrue(positions.isEmpty())
        positions = manager!!.run(aStrategy, 9, 9).positions
        Assert.assertTrue(positions.isEmpty())
    }
}
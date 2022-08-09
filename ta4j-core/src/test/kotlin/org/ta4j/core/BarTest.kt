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
import org.ta4j.core.num.Num
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.function.Function

class BarTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(null, numFunction) {
    private var bar: Bar? = null
    private var beginTime: ZonedDateTime? = null
    private var endTime: ZonedDateTime? = null
    @Before
    fun setUp() {
        beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault())
        endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault())
        bar = BaseBar(Duration.ofHours(1), endTime!!, numFunction)
    }

    @Test
    fun addTrades() {
        bar!!.addTrade(3.0, 200.0, numFunction)
        bar!!.addTrade(4.0, 201.0, numFunction)
        bar!!.addTrade(2.0, 198.0, numFunction)
        Assert.assertEquals(3, bar!!.trades)
        TestUtils.assertNumEquals(3 * 200 + 4 * 201 + 2 * 198, bar!!.amount)
        TestUtils.assertNumEquals(200, bar!!.openPrice)
        TestUtils.assertNumEquals(198, bar!!.closePrice)
        TestUtils.assertNumEquals(198, bar!!.lowPrice)
        TestUtils.assertNumEquals(201, bar!!.highPrice)
        TestUtils.assertNumEquals(9, bar!!.volume)
    }

    @Test
    fun getTimePeriod() {
        Assert.assertEquals(beginTime, bar!!.endTime!!.minus(bar!!.timePeriod))
    }

    @Test
    fun getBeginTime() {
        Assert.assertEquals(beginTime, bar!!.beginTime)
    }

    @Test
    fun inPeriod() {
        Assert.assertFalse(bar!!.inPeriod(null))
        Assert.assertFalse(bar!!.inPeriod(beginTime!!.withDayOfMonth(24)))
        Assert.assertFalse(bar!!.inPeriod(beginTime!!.withDayOfMonth(26)))
        Assert.assertTrue(bar!!.inPeriod(beginTime!!.withMinute(30)))
        Assert.assertTrue(bar!!.inPeriod(beginTime))
        Assert.assertFalse(bar!!.inPeriod(endTime))
    }

    @Test
    fun equals() {
        val bar1: Bar = BaseBar(Duration.ofHours(1), endTime!!, numFunction)
        val bar2: Bar = BaseBar(Duration.ofHours(1), endTime!!, numFunction)
        Assert.assertEquals(bar1, bar2)
    }

    @Test
    fun hashCode2() {
        val bar1: Bar = BaseBar(Duration.ofHours(1), endTime!!, numFunction)
        val bar2: Bar = BaseBar(Duration.ofHours(1), endTime!!, numFunction)
        Assert.assertEquals(bar1.hashCode().toLong(), bar2.hashCode().toLong())
    }
}
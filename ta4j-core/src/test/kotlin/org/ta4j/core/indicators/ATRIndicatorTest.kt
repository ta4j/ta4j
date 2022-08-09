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
package org.ta4j.core.indicators

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

class ATRIndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(
    IndicatorFactory { data: BarSeries?, params: Array<out Any?> -> ATRIndicator(data, params[0] as Int) }, numFunction
) {
    private val xls: ExternalIndicatorTest

    init {
        xls = XLSIndicatorTest(this.javaClass, "ATR.xls", 7, numFunction)
    }

    @Test
    @Throws(Exception::class)
    fun testDummy() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        series.addBar(MockBar(ZonedDateTime.now().minusSeconds(5), 0.0, 12.0, 15.0, 8.0, 0.0, 0.0, 0, numFunction))
        series.addBar(MockBar(ZonedDateTime.now().minusSeconds(4), 0.0, 8.0, 11.0, 6.0, 0.0, 0.0, 0, numFunction))
        series.addBar(MockBar(ZonedDateTime.now().minusSeconds(3), 0.0, 15.0, 17.0, 14.0, 0.0, 0.0, 0, numFunction))
        series.addBar(MockBar(ZonedDateTime.now().minusSeconds(2), 0.0, 15.0, 17.0, 14.0, 0.0, 0.0, 0, numFunction))
        series.addBar(MockBar(ZonedDateTime.now().minusSeconds(1), 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0, numFunction))
        val indicator = getIndicator(series, 3)
        Assert.assertEquals(7.0, indicator[0].doubleValue(), TestUtils.GENERAL_OFFSET)
        Assert.assertEquals(
            6.0 / 3 + (1 - 1.0 / 3) * indicator[0]
                .doubleValue(), indicator[1].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        Assert.assertEquals(
            9.0 / 3 + (1 - 1.0 / 3) * indicator[1].doubleValue(),
            indicator[2].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        Assert.assertEquals(
            3.0 / 3 + (1 - 1.0 / 3) * indicator[2]
                .doubleValue(), indicator[3].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        Assert.assertEquals(
            15.0 / 3 + (1 - 1.0 / 3) * indicator[3]
                .doubleValue(), indicator[4].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
    }

    @Test
    @Throws(Exception::class)
    fun testXls() {
        val xlsSeries = xls.getSeries()
        var indicator = getIndicator(xlsSeries, 1)
        TestUtils.assertIndicatorEquals(xls.getIndicator(1), indicator)
        Assert.assertEquals(
            4.8, indicator[indicator.barSeries!!.endIndex].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        indicator = getIndicator(xlsSeries, 3)
        TestUtils.assertIndicatorEquals(xls.getIndicator(3), indicator)
        Assert.assertEquals(
            7.4225, indicator[indicator.barSeries!!.endIndex].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        indicator = getIndicator(xlsSeries, 13)
        TestUtils.assertIndicatorEquals(xls.getIndicator(13), indicator)
        Assert.assertEquals(
            8.8082, indicator[indicator.barSeries!!.endIndex].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
    }
}
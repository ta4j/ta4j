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
import org.junit.Before
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class SMAIndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>?, Num>(
    IndicatorFactory { data: Indicator<Num>?, params: Array<out Any?> -> SMAIndicator(data!!, params[0] as Int) }, numFunction
) {
    private val xls: ExternalIndicatorTest
    private var data: BarSeries? = null

    init {
        xls = XLSIndicatorTest(this.javaClass, "SMA.xls", 6, numFunction)
    }

    @Before
    fun setUp() {
        data = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0, 3.0, 4.0, 3.0, 2.0)
    }

    @Test
    @Throws(Exception::class)
    fun usingBarCount3UsingClosePrice() {
        val indicator = getIndicator(ClosePriceIndicator(data), 3)
        TestUtils.assertNumEquals(1, indicator[0])
        TestUtils.assertNumEquals(1.5, indicator.getValue(1))
        TestUtils.assertNumEquals(2, indicator.getValue(2))
        TestUtils.assertNumEquals(3, indicator.getValue(3))
        TestUtils.assertNumEquals(10.0 / 3, indicator.getValue(4))
        TestUtils.assertNumEquals(11.0 / 3, indicator.getValue(5))
        TestUtils.assertNumEquals(4, indicator.getValue(6))
        TestUtils.assertNumEquals(13.0 / 3, indicator.getValue(7))
        TestUtils.assertNumEquals(4, indicator.getValue(8))
        TestUtils.assertNumEquals(10.0 / 3, indicator.getValue(9))
        TestUtils.assertNumEquals(10.0 / 3, indicator.getValue(10))
        TestUtils.assertNumEquals(10.0 / 3, indicator.getValue(11))
        TestUtils.assertNumEquals(3, indicator.getValue(12))
    }

    @Test
    @Throws(Exception::class)
    fun whenBarCountIs1ResultShouldBeIndicatorValue() {
        val indicator = getIndicator(ClosePriceIndicator(data), 1)
        for (i in 0 until data!!.barCount) {
            Assert.assertEquals(data!!.getBar(i).closePrice, indicator[i])
        }
    }

    @Test
    @Throws(Exception::class)
    fun externalData() {
        val xlsClose: Indicator<Num> = ClosePriceIndicator(xls.getSeries())
        var actualIndicator: Indicator<Num>?
        actualIndicator = getIndicator(xlsClose, 1)
        TestUtils.assertIndicatorEquals(xls.getIndicator(1), actualIndicator)
        Assert.assertEquals(
            329.0, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        actualIndicator = getIndicator(xlsClose, 3)
        TestUtils.assertIndicatorEquals(xls.getIndicator(3), actualIndicator)
        Assert.assertEquals(
            326.6333, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        actualIndicator = getIndicator(xlsClose, 13)
        TestUtils.assertIndicatorEquals(xls.getIndicator(13), actualIndicator)
        Assert.assertEquals(
            327.7846, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
    }
}
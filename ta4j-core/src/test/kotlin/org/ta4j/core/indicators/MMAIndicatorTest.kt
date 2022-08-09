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
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class MMAIndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>, Num>(
    IndicatorFactory { data: Indicator<Num>, params: Array<out Any?> -> MMAIndicator(data, params[0] as Int) }, numFunction
) {
    private val xls: ExternalIndicatorTest
    private var data: BarSeries? = null

    init {
        xls = XLSIndicatorTest(this.javaClass, "MMA.xls", 6, numFunction)
    }

    @Before
    fun setUp() {
        data = MockBarSeries(
            numFunction, 64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95, 63.37,
            61.33, 61.51
        )
    }

    @Test
    @Throws(Exception::class)
    fun firstValueShouldBeEqualsToFirstDataValue() {
        val actualIndicator = getIndicator(ClosePriceIndicator(data), 1)
        Assert.assertEquals(64.75, actualIndicator[0].doubleValue(), TestUtils.GENERAL_OFFSET)
    }

    @Test
    @Throws(Exception::class)
    fun mmaUsingBarCount10UsingClosePrice() {
        val actualIndicator = getIndicator(ClosePriceIndicator(data), 10)
        Assert.assertEquals(63.9983, actualIndicator.getValue(9).doubleValue(), TestUtils.GENERAL_OFFSET)
        Assert.assertEquals(63.7315, actualIndicator.getValue(10).doubleValue(), TestUtils.GENERAL_OFFSET)
        Assert.assertEquals(63.5093, actualIndicator.getValue(11).doubleValue(), TestUtils.GENERAL_OFFSET)
    }

    @Test
    @Throws(Exception::class)
    fun stackOverflowError() {
        val bigListOfBars: MutableList<Bar> = ArrayList()
        for (i in 0..9999) {
            bigListOfBars.add(MockBar(i.toDouble(), numFunction))
        }
        val bigSeries = MockBarSeries(bigListOfBars)
        val closePrice = ClosePriceIndicator(bigSeries)
        val actualIndicator = getIndicator(closePrice, 10)
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        Assert.assertEquals(9990.0, actualIndicator.getValue(9999).doubleValue(), TestUtils.GENERAL_OFFSET)
    }

    @Test
    @Throws(Exception::class)
    fun testAgainstExternalData() {
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
            327.2900, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        actualIndicator = getIndicator(xlsClose, 13)
        TestUtils.assertIndicatorEquals(xls.getIndicator(13), actualIndicator)
        Assert.assertEquals(
            326.9696, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
    }
}
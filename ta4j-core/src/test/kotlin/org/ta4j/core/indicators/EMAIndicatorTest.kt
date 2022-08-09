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

class EMAIndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>?, Num>(
    IndicatorFactory { data: Indicator<Num>?, params: Array<out Any?> -> EMAIndicator(data!!, params[0] as Int) }, numFunction
) {
    private val xls: ExternalIndicatorTest
    private var data: BarSeries? = null

    init {
        xls = XLSIndicatorTest(this.javaClass, "EMA.xls", 6, numFunction)
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
        val indicator = getIndicator(ClosePriceIndicator(data), 1)
        TestUtils.assertNumEquals(64.75, indicator[0])
    }

    @Test
    @Throws(Exception::class)
    fun usingBarCount10UsingClosePrice() {
        val indicator = getIndicator(ClosePriceIndicator(data), 10)
        TestUtils.assertNumEquals(63.6948, indicator[9])
        TestUtils.assertNumEquals(63.2648, indicator[10])
        TestUtils.assertNumEquals(62.9457, indicator[11])
    }

    @Test
    @Throws(Exception::class)
    fun stackOverflowError() {
        val bigListOfBars: MutableList<Bar> = ArrayList()
        for (i in 0..9999) {
            bigListOfBars.add(MockBar(i.toDouble(), numFunction))
        }
        val bigSeries = MockBarSeries(bigListOfBars)
        val indicator = getIndicator(ClosePriceIndicator(bigSeries), 10)
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        TestUtils.assertNumEquals(9994.5, indicator[9999])
    }

    @Test
    @Throws(Exception::class)
    fun externalData() {
        val xlsSeries = xls.getSeries()
        val closePrice: Indicator<Num> = ClosePriceIndicator(xlsSeries)
        var indicator: Indicator<Num>?
        indicator = getIndicator(closePrice, 1)
        TestUtils.assertIndicatorEquals(xls.getIndicator(1), indicator)
        Assert.assertEquals(
            329.0, indicator[indicator.barSeries!!.endIndex].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        indicator = getIndicator(closePrice, 3)
        TestUtils.assertIndicatorEquals(xls.getIndicator(3), indicator)
        Assert.assertEquals(
            327.7748, indicator[indicator.barSeries!!.endIndex].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        indicator = getIndicator(closePrice, 13)
        TestUtils.assertIndicatorEquals(xls.getIndicator(13), indicator)
        Assert.assertEquals(
            327.4076, indicator[indicator.barSeries!!.endIndex].doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
    }
}
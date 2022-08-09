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
package org.ta4j.core.indicators.adx

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.XLSIndicatorTest
import org.ta4j.core.num.Num
import java.util.function.Function

class PlusDIIndicatorTest(nf: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(
    IndicatorFactory { data: BarSeries?, params: Array<out Any?> -> PlusDIIndicator(data, params[0] as Int) }, nf
) {
    private val xls: ExternalIndicatorTest

    init {
        xls = XLSIndicatorTest(this.javaClass, "ADX.xls", 12, numFunction)
    }

    @Test
    @Throws(Exception::class)
    fun testAgainstExternalData() {
        val xlsSeries = xls.getSeries()
        var actualIndicator: Indicator<Num>?
        actualIndicator = getIndicator(xlsSeries, 1)
        TestUtils.assertIndicatorEquals(xls.getIndicator(1), actualIndicator)
        Assert.assertEquals(
            12.5, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        actualIndicator = getIndicator(xlsSeries, 3)
        TestUtils.assertIndicatorEquals(xls.getIndicator(3), actualIndicator)
        Assert.assertEquals(
            22.8407, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
        actualIndicator = getIndicator(xlsSeries, 13)
        TestUtils.assertIndicatorEquals(xls.getIndicator(13), actualIndicator)
        Assert.assertEquals(
            22.1399, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex).doubleValue(),
            TestUtils.GENERAL_OFFSET
        )
    }
}
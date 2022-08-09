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

import org.junit.Before
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class StochasticRSIIndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>, Num>(
    IndicatorFactory { data: Indicator<Num>?, params: Array<out Any> -> StochasticRSIIndicator(data!!, params[0] as Int) },
    numFunction
) {
    private var data: BarSeries? = null
    private val xls: ExternalIndicatorTest

    init {
        xls = XLSIndicatorTest(this.javaClass, "AAPL_StochRSI.xls", 15, numFunction)
    }

    @Test
    @Throws(Exception::class)
    fun xlsTest() {
        val xlsSeries = xls.getSeries()
        val xlsClose: Indicator<Num> = ClosePriceIndicator(xlsSeries)
        val actualIndicator = getIndicator(xlsClose, 14)
        TestUtils.assertIndicatorEquals(xls.getIndicator(14), actualIndicator)
        TestUtils.assertNumEquals(0.5223, actualIndicator.getValue(actualIndicator.barSeries!!.endIndex - 1))
    }

    @Before
    fun setUp() {
        data = MockBarSeries(
            numFunction, 50.45, 50.30, 50.20, 50.15, 50.05, 50.06, 50.10, 50.08, 50.03, 50.07,
            50.01, 50.14, 50.22, 50.43, 50.50, 50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86, 51.20, 51.30,
            51.10
        )
    }

    @Test
    fun stochasticRSI() {
        val srsi = StochasticRSIIndicator(data, 14)
        TestUtils.assertNumEquals(1, srsi.getValue(15))
        TestUtils.assertNumEquals(0.9460, srsi.getValue(16))
        TestUtils.assertNumEquals(1, srsi.getValue(17))
        TestUtils.assertNumEquals(0.8365, srsi.getValue(18))
        TestUtils.assertNumEquals(0.8610, srsi.getValue(19))
        TestUtils.assertNumEquals(1, srsi.getValue(20))
        TestUtils.assertNumEquals(0.9186, srsi.getValue(21))
        TestUtils.assertNumEquals(0.9305, srsi.getValue(22))
        TestUtils.assertNumEquals(1, srsi.getValue(23))
        TestUtils.assertNumEquals(1, srsi.getValue(24))
    }
}
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
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class RAVIIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        data = MockBarSeries(
            numFunction, 110.00, 109.27, 104.69, 107.07, 107.92, 107.95, 108.70, 107.97, 106.09,
            106.03, 108.65, 109.54, 112.26, 114.38, 117.94
        )
    }

    @Test
    fun ravi() {
        val closePrice = ClosePriceIndicator(data)
        val ravi = RAVIIndicator(closePrice, 3, 8)
        TestUtils.assertNumEquals(0, ravi[0])
        TestUtils.assertNumEquals(0, ravi.getValue(1))
        TestUtils.assertNumEquals(0, ravi.getValue(2))
        TestUtils.assertNumEquals(-0.6937, ravi.getValue(3))
        TestUtils.assertNumEquals(-1.1411, ravi.getValue(4))
        TestUtils.assertNumEquals(-0.1577, ravi.getValue(5))
        TestUtils.assertNumEquals(0.229, ravi.getValue(6))
        TestUtils.assertNumEquals(0.2412, ravi.getValue(7))
        TestUtils.assertNumEquals(0.1202, ravi.getValue(8))
        TestUtils.assertNumEquals(-0.3324, ravi.getValue(9))
        TestUtils.assertNumEquals(-0.5804, ravi.getValue(10))
        TestUtils.assertNumEquals(0.2013, ravi.getValue(11))
        TestUtils.assertNumEquals(1.6156, ravi.getValue(12))
        TestUtils.assertNumEquals(2.6167, ravi.getValue(13))
        TestUtils.assertNumEquals(4.0799, ravi.getValue(14))
    }
}
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

class LWMAIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        data = MockBarSeries(
            numFunction, 37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79,
            36.83
        )
    }

    @Test
    fun lwmaUsingBarCount5UsingClosePrice() {
        val lwma = LWMAIndicator(ClosePriceIndicator(data), 5)
        TestUtils.assertNumEquals(0.0, lwma[0])
        TestUtils.assertNumEquals(0.0, lwma.getValue(1))
        TestUtils.assertNumEquals(0.0, lwma.getValue(2))
        TestUtils.assertNumEquals(0.0, lwma.getValue(3))
        TestUtils.assertNumEquals(36.0506, lwma.getValue(4))
        TestUtils.assertNumEquals(35.9673, lwma.getValue(5))
        TestUtils.assertNumEquals(36.0766, lwma.getValue(6))
        TestUtils.assertNumEquals(36.6253, lwma.getValue(7))
        TestUtils.assertNumEquals(37.1833, lwma.getValue(8))
        TestUtils.assertNumEquals(37.5240, lwma.getValue(9))
        TestUtils.assertNumEquals(37.4060, lwma.getValue(10))
    }
}
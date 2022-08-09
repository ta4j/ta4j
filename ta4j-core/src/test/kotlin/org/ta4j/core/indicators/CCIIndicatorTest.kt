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
import org.ta4j.core.Bar
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class CCIIndicatorTest
/**
 * Constructor.
 *
 * @param function
 */
    (function: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>?, Num>(function) {
    private val typicalPrices = doubleArrayOf(
        23.98, 23.92, 23.79, 23.67, 23.54, 23.36, 23.65, 23.72, 24.16,
        23.91, 23.81, 23.92, 23.74, 24.68, 24.94, 24.93, 25.10, 25.12, 25.20, 25.06, 24.50, 24.31, 24.57, 24.62,
        24.49, 24.37, 24.41, 24.35, 23.75, 24.09
    )
    private var series: MockBarSeries? = null
    @Before
    fun setUp() {
        val bars = ArrayList<Bar>()
        for (price in typicalPrices) {
            bars.add(MockBar(price, price, price, price, numFunction))
        }
        series = MockBarSeries(bars)
    }

    @Test
    fun getValueWhenBarCountIs20() {
        val cci = CCIIndicator(series, 20)

        // Incomplete time frame
        TestUtils.assertNumEquals(0, cci[0])
        TestUtils.assertNumEquals(-66.6667, cci.getValue(1))
        TestUtils.assertNumEquals(-100.0, cci.getValue(2))
        TestUtils.assertNumEquals(14.365, cci.getValue(10))
        TestUtils.assertNumEquals(54.2544, cci.getValue(11))

        // Complete time frame
        val results20to30 = doubleArrayOf(
            101.9185, 31.1946, 6.5578, 33.6078, 34.9686, 13.6027, -10.6789, -11.471,
            -29.2567, -128.6, -72.7273
        )
        for (i in results20to30.indices) {
            TestUtils.assertNumEquals(results20to30[i], cci.getValue(i + 19))
        }
    }
}
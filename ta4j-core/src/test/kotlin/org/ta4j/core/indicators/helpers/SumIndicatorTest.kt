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
package org.ta4j.core.indicators.helpers

import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.TestUtils

class SumIndicatorTest {
    private var sumIndicator: SumIndicator? = null
    @Before
    fun setUp() {
        val series: BarSeries = BaseBarSeries()
        val constantIndicator = ConstantIndicator(series, series.numOf(6))
        val mockIndicator = FixedIndicator(
            series, series.numOf(-2.0), series.numOf(0.00),
            series.numOf(1.00), series.numOf(2.53), series.numOf(5.87), series.numOf(6.00), series.numOf(10.0)
        )
        val mockIndicator2 = FixedIndicator(
            series, series.numOf(0), series.numOf(1),
            series.numOf(2), series.numOf(3), series.numOf(10), series.numOf(-42), series.numOf(-1337)
        )
        sumIndicator = SumIndicator(constantIndicator, mockIndicator, mockIndicator2)
    }

    @Test
    fun getValue() {
        TestUtils.assertNumEquals("4.0", sumIndicator!![0])
        TestUtils.assertNumEquals("7.0", sumIndicator!!.getValue(1))
        TestUtils.assertNumEquals("9.0", sumIndicator!!.getValue(2))
        TestUtils.assertNumEquals("11.53", sumIndicator!!.getValue(3))
        TestUtils.assertNumEquals("21.87", sumIndicator!!.getValue(4))
        TestUtils.assertNumEquals("-30.0", sumIndicator!!.getValue(5))
        TestUtils.assertNumEquals("-1321.0", sumIndicator!!.getValue(6))
    }
}
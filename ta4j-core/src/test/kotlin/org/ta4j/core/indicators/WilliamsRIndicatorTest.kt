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
import org.ta4j.core.indicators.helpers.HighPriceIndicator
import org.ta4j.core.indicators.helpers.LowPriceIndicator
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.util.function.Function

class WilliamsRIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(44.98, 45.05, 45.17, 44.96, numFunction))
        bars.add(MockBar(45.05, 45.10, 45.15, 44.99, numFunction))
        bars.add(MockBar(45.11, 45.19, 45.32, 45.11, numFunction))
        bars.add(MockBar(45.19, 45.14, 45.25, 45.04, numFunction))
        bars.add(MockBar(45.12, 45.15, 45.20, 45.10, numFunction))
        bars.add(MockBar(45.15, 45.14, 45.20, 45.10, numFunction))
        bars.add(MockBar(45.13, 45.10, 45.16, 45.07, numFunction))
        bars.add(MockBar(45.12, 45.15, 45.22, 45.10, numFunction))
        bars.add(MockBar(45.15, 45.22, 45.27, 45.14, numFunction))
        bars.add(MockBar(45.24, 45.43, 45.45, 45.20, numFunction))
        bars.add(MockBar(45.43, 45.44, 45.50, 45.39, numFunction))
        bars.add(MockBar(45.43, 45.55, 45.60, 45.35, numFunction))
        bars.add(MockBar(45.58, 45.55, 45.61, 45.39, numFunction))
        data = BaseBarSeries(bars)
    }

    @Test
    fun williamsRUsingBarCount5UsingClosePrice() {
        val wr = WilliamsRIndicator(
            ClosePriceIndicator(data), 5, HighPriceIndicator(data),
            LowPriceIndicator(data)
        )
        TestUtils.assertNumEquals(-47.2222, wr.getValue(4))
        TestUtils.assertNumEquals(-54.5454, wr.getValue(5))
        TestUtils.assertNumEquals(-78.5714, wr.getValue(6))
        TestUtils.assertNumEquals(-47.6190, wr.getValue(7))
        TestUtils.assertNumEquals(-25.0, wr.getValue(8))
        TestUtils.assertNumEquals(-5.2632, wr.getValue(9))
        TestUtils.assertNumEquals(-13.9535, wr.getValue(10))
    }

    @Test
    fun williamsRUsingBarCount10UsingClosePrice() {
        val wr = WilliamsRIndicator(
            ClosePriceIndicator(data), 10, HighPriceIndicator(data),
            LowPriceIndicator(data)
        )
        TestUtils.assertNumEquals(-4.0816, wr.getValue(9))
        TestUtils.assertNumEquals(-11.7647, wr.getValue(10))
        TestUtils.assertNumEquals(-8.9286, wr.getValue(11))
        TestUtils.assertNumEquals(-10.5263, wr.getValue(12))
    }

    @Test
    fun valueLessThenBarCount() {
        val wr = WilliamsRIndicator(
            ClosePriceIndicator(data), 100, HighPriceIndicator(data),
            LowPriceIndicator(data)
        )
        TestUtils.assertNumEquals(-100.0 * (0.12 / 0.21), wr[0])
        TestUtils.assertNumEquals(-100.0 * (0.07 / 0.21), wr.getValue(1))
        TestUtils.assertNumEquals(-100.0 * (0.13 / 0.36), wr.getValue(2))
        TestUtils.assertNumEquals(-100.0 * (0.18 / 0.36), wr.getValue(3))
    }
}
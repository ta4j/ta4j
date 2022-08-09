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
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class PVOIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var barSeries: BarSeries? = null
    @Before
    fun setUp() {
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(0.0, 10.0, numFunction))
        bars.add(MockBar(0.0, 11.0, numFunction))
        bars.add(MockBar(0.0, 12.0, numFunction))
        bars.add(MockBar(0.0, 13.0, numFunction))
        bars.add(MockBar(0.0, 150.0, numFunction))
        bars.add(MockBar(0.0, 155.0, numFunction))
        bars.add(MockBar(0.0, 160.0, numFunction))
        barSeries = MockBarSeries(bars)
    }

    @Test
    fun createPvoIndicator() {
        val pvo = PVOIndicator(barSeries)
        TestUtils.assertNumEquals(0.791855204, pvo.getValue(1))
        TestUtils.assertNumEquals(2.164434756, pvo.getValue(2))
        TestUtils.assertNumEquals(3.925400464, pvo.getValue(3))
        TestUtils.assertNumEquals(55.296120101, pvo.getValue(4))
        TestUtils.assertNumEquals(66.511722064, pvo.getValue(5))
    }
}
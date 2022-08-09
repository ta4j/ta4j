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
import org.ta4j.core.indicators.helpers.MedianPriceIndicator
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class AwesomeOscillatorIndicatorTest
/**
 * Constructor.
 *
 * @param function
 */
    (function: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>?, Num>(function) {
    private var series: BarSeries? = null
    @Before
    fun setUp() {
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(0.0, 0.0, 16.0, 8.0, numFunction))
        bars.add(MockBar(0.0, 0.0, 12.0, 6.0, numFunction))
        bars.add(MockBar(0.0, 0.0, 18.0, 14.0, numFunction))
        bars.add(MockBar(0.0, 0.0, 10.0, 6.0, numFunction))
        bars.add(MockBar(0.0, 0.0, 8.0, 4.0, numFunction))
        series = MockBarSeries(bars)
    }

    @Test
    fun calculateWithSma2AndSma3() {
        val awesome = AwesomeOscillatorIndicator(MedianPriceIndicator(series), 2, 3)
        TestUtils.assertNumEquals(0, awesome[0])
        TestUtils.assertNumEquals(0, awesome.getValue(1))
        TestUtils.assertNumEquals(1.0 / 6, awesome.getValue(2))
        TestUtils.assertNumEquals(1, awesome.getValue(3))
        TestUtils.assertNumEquals(-3, awesome.getValue(4))
    }

    @Test
    fun withSma1AndSma2() {
        val awesome = AwesomeOscillatorIndicator(MedianPriceIndicator(series), 1, 2)
        TestUtils.assertNumEquals(0, awesome[0])
        TestUtils.assertNumEquals("-1.5", awesome.getValue(1))
        TestUtils.assertNumEquals("3.5", awesome.getValue(2))
        TestUtils.assertNumEquals(-4, awesome.getValue(3))
        TestUtils.assertNumEquals(-1, awesome.getValue(4))
    }

    @Test
    fun withSmaDefault() {
        val awesome = AwesomeOscillatorIndicator(MedianPriceIndicator(series))
        TestUtils.assertNumEquals(0, awesome[0])
        TestUtils.assertNumEquals(0, awesome.getValue(1))
        TestUtils.assertNumEquals(0, awesome.getValue(2))
        TestUtils.assertNumEquals(0, awesome.getValue(3))
        TestUtils.assertNumEquals(0, awesome.getValue(4))
    }
}
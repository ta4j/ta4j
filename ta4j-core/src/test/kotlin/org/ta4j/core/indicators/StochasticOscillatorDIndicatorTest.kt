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

import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.util.function.Function

class StochasticOscillatorDIndicatorTest(function: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(function) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(44.98, 119.13, 119.50, 116.00, numFunction))
        bars.add(MockBar(45.05, 116.75, 119.94, 116.00, numFunction))
        bars.add(MockBar(45.11, 113.50, 118.44, 111.63, numFunction))
        bars.add(MockBar(45.19, 111.56, 114.19, 110.06, numFunction))
        bars.add(MockBar(45.12, 112.25, 112.81, 109.63, numFunction))
        bars.add(MockBar(45.15, 110.00, 113.44, 109.13, numFunction))
        bars.add(MockBar(45.13, 113.50, 115.81, 110.38, numFunction))
        bars.add(MockBar(45.12, 117.13, 117.50, 114.06, numFunction))
        bars.add(MockBar(45.15, 115.63, 118.44, 114.81, numFunction))
        bars.add(MockBar(45.24, 114.13, 116.88, 113.13, numFunction))
        bars.add(MockBar(45.43, 118.81, 119.00, 116.19, numFunction))
        bars.add(MockBar(45.43, 117.38, 119.75, 117.00, numFunction))
        bars.add(MockBar(45.58, 119.13, 119.13, 116.88, numFunction))
        bars.add(MockBar(45.58, 115.38, 119.44, 114.56, numFunction))
        data = BaseBarSeries(bars)
    }

    @Test
    fun stochasticOscilatorDParam14UsingSMA3AndGenericConstructer() {
        val sof = StochasticOscillatorKIndicator(data, 14)
        val sma = SMAIndicator(sof, 3)
        val sos = StochasticOscillatorDIndicator(sma)
        TestCase.assertEquals(sma[0], sos[0])
        TestCase.assertEquals(sma.getValue(1), sos.getValue(1))
        TestCase.assertEquals(sma.getValue(2), sos.getValue(2))
    }

    @Test
    fun stochasticOscilatorDParam14UsingSMA3() {
        val sof = StochasticOscillatorKIndicator(data, 14)
        val sos = StochasticOscillatorDIndicator(sof)
        val sma = SMAIndicator(sof, 3)
        TestCase.assertEquals(sma[0], sos[0])
        TestCase.assertEquals(sma.getValue(1), sos.getValue(1))
        TestCase.assertEquals(sma.getValue(2), sos.getValue(2))
        TestCase.assertEquals(sma.getValue(13), sos.getValue(13))
    }
}
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
package org.ta4j.core.indicators.candles

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class BullishEngulfingIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var series: BarSeries? = null
    @Before
    fun setUp() {
        val bars: MutableList<Bar> = ArrayList()
        // open, close, high, low
        bars.add(MockBar(10.0, 18.0, 20.0, 10.0, numFunction))
        bars.add(MockBar(17.0, 16.0, 19.0, 15.0, numFunction))
        bars.add(MockBar(15.0, 18.0, 19.0, 14.0, numFunction))
        bars.add(MockBar(15.0, 11.0, 15.0, 8.0, numFunction))
        bars.add(MockBar(11.0, 12.0, 12.0, 10.0, numFunction))
        series = MockBarSeries(bars)
    }

    @Test
    fun getValue() {
        val bep = BullishEngulfingIndicator(series)
        Assert.assertFalse(bep[0])
        Assert.assertFalse(bep.getValue(1))
        Assert.assertTrue(bep.getValue(2))
        Assert.assertFalse(bep.getValue(3))
        Assert.assertFalse(bep.getValue(4))
    }
}
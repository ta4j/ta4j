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

class DistanceFromMAIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        data = MockBarSeries(numFunction, 10.0, 15.0, 20.0, 18.0, 17.0, 18.0, 15.0, 12.0, 10.0, 8.0, 5.0, 2.0)
    }

    @Test
    fun DistanceFromMovingAverageTest() {
        val sma = SMAIndicator(ClosePriceIndicator(data), 3)
        val distanceFromMAIndicator = DistanceFromMAIndicator(data, sma)
        TestUtils.assertNumEquals(0.3333, distanceFromMAIndicator.getValue(2))
        TestUtils.assertNumEquals(0.01886792452830182, distanceFromMAIndicator.getValue(5))
        TestUtils.assertNumEquals(-0.1, distanceFromMAIndicator.getValue(6))
    }

    @Test(expected = IllegalArgumentException::class)
    fun DistanceFromIllegalMovingAverage() {
        val closePriceIndicator = ClosePriceIndicator(data)
        DistanceFromMAIndicator(data, closePriceIndicator)
    }
}
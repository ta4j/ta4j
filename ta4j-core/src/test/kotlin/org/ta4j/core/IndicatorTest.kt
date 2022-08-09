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
package org.ta4j.core

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Indicator.Companion.toDouble
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.mocks.MockIndicator
import org.ta4j.core.num.Num
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors

class IndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    var typicalPrices = doubleArrayOf(
        23.98, 23.92, 23.79, 23.67, 23.54, 23.36, 23.65, 23.72, 24.16, 23.91, 23.81, 23.92,
        23.74, 24.68, 24.94, 24.93, 25.10, 25.12, 25.20, 25.06, 24.50, 24.31, 24.57, 24.62, 24.49, 24.37, 24.41,
        24.35, 23.75, 24.09
    )
    var data: BarSeries? = null
    @Before
    fun setUp() {
        data = MockBarSeries(numFunction, *typicalPrices)
    }

    @Test
    fun toDouble() {
        val expectedValues = Arrays.stream(typicalPrices)
            .mapToObj { t: Double -> numFunction.apply(t) }
            .collect(Collectors.toList())
        val closePriceMockIndicator = MockIndicator(data, expectedValues)
        val barCount = 10
        val index = 20
        val doubles = toDouble(closePriceMockIndicator, index, barCount)
        Assert.assertTrue(doubles.size == barCount)
        for (i in 0 until barCount) {
            Assert.assertTrue(typicalPrices[i + 11] == doubles[i])
        }
    }

    @Test
    fun shouldProvideStream() {
        val expectedValues = Arrays.stream(typicalPrices)
            .mapToObj { t: Double -> numFunction.apply(t) }
            .collect(Collectors.toList())
        val closePriceMockIndicator = MockIndicator(data, expectedValues)
        val stream = closePriceMockIndicator.stream()
        val collectedValues = stream.collect(Collectors.toList())
        Assert.assertNotNull(stream)
        Assert.assertNotNull(collectedValues)
        Assert.assertEquals(30, collectedValues.size.toLong())
        for (i in 0 until data!!.barCount) {
            TestUtils.assertNumEquals(typicalPrices[i], collectedValues[i])
        }
    }
}
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

import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

class LowestValueIndicatorTest(function: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(function) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        data = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 3.0, 4.0, 5.0, 6.0, 4.0, 3.0, 2.0, 4.0, 3.0, 1.0)
    }

    @Test
    fun lowestValueIndicatorUsingBarCount5UsingClosePrice() {
        val lowestValue = LowestValueIndicator(ClosePriceIndicator(data), 5)
        TestUtils.assertNumEquals("1.0", lowestValue.getValue(1))
        TestUtils.assertNumEquals("1.0", lowestValue.getValue(2))
        TestUtils.assertNumEquals("1.0", lowestValue.getValue(3))
        TestUtils.assertNumEquals("1.0", lowestValue.getValue(4))
        TestUtils.assertNumEquals("2.0", lowestValue.getValue(5))
        TestUtils.assertNumEquals("3.0", lowestValue.getValue(6))
        TestUtils.assertNumEquals("3.0", lowestValue.getValue(7))
        TestUtils.assertNumEquals("3.0", lowestValue.getValue(8))
        TestUtils.assertNumEquals("3.0", lowestValue.getValue(9))
        TestUtils.assertNumEquals("2.0", lowestValue.getValue(10))
        TestUtils.assertNumEquals("2.0", lowestValue.getValue(11))
        TestUtils.assertNumEquals("2.0", lowestValue.getValue(12))
    }

    @Test
    fun lowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        val lowestValue = LowestValueIndicator(ClosePriceIndicator(data), 5)
        TestUtils.assertNumEquals("1.0", lowestValue[0])
    }

    @Test
    fun lowestValueIndicatorWhenBarCountIsGreaterThanIndex() {
        val lowestValue = LowestValueIndicator(ClosePriceIndicator(data), 500)
        TestUtils.assertNumEquals("1.0", lowestValue.getValue(12))
    }

    @Test
    fun onlyNaNValues() {
        val series = BaseBarSeries("NaN test")
        for (i in 0..10000) {
            series.addBar(ZonedDateTime.now().plusDays(i.toLong()), NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN)
        }
        val lowestValue = LowestValueIndicator(ClosePriceIndicator(series), 5)
        for (i in series.beginIndex..series.endIndex) {
            TestCase.assertEquals(NaN.NaN.toString(), lowestValue[i].toString())
        }
    }

    @Test
    fun naNValuesInIntervall() {
        val series = BaseBarSeries("NaN test")
        for (i in 0..10) { // (NaN, 1, NaN, 2, NaN, 3, NaN, 4, ...)
            series.addBar(ZonedDateTime.now().plusDays(i.toLong()), NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN)
        }
        val lowestValue = LowestValueIndicator(ClosePriceIndicator(series), 2)
        for (i in series.beginIndex..series.endIndex) {
            if (i % 2 != 0) {
                TestCase.assertEquals(series.getBar(i - 1).closePrice.toString(), lowestValue[i].toString())
            } else TestCase.assertEquals(
                series.getBar(Math.max(0, i - 1)).closePrice.toString(),
                lowestValue[i].toString()
            )
        }
    }
}
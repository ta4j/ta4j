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

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.TestUtils

class FixedIndicatorTest {
    @Test
    fun getValueOnFixedDecimalIndicator() {
        val series: BarSeries = BaseBarSeries()
        var fixedDecimalIndicator = FixedDecimalIndicator(series, 13.37, 42.0, -17.0)
        TestUtils.assertNumEquals(13.37, fixedDecimalIndicator[0])
        TestUtils.assertNumEquals(42, fixedDecimalIndicator.getValue(1))
        TestUtils.assertNumEquals(-17, fixedDecimalIndicator.getValue(2))
        fixedDecimalIndicator = FixedDecimalIndicator(series, "3.0", "-123.456", "0.0")
        TestUtils.assertNumEquals("3.0", fixedDecimalIndicator[0])
        TestUtils.assertNumEquals("-123.456", fixedDecimalIndicator.getValue(1))
        TestUtils.assertNumEquals("0.0", fixedDecimalIndicator.getValue(2))
    }

    @Test
    fun getValueOnFixedBooleanIndicator() {
        val series: BarSeries = BaseBarSeries()
        val fixedBooleanIndicator = FixedBooleanIndicator(
            series, false, false, true, false,
            true
        )
        Assert.assertFalse(fixedBooleanIndicator[0]!!)
        Assert.assertFalse(fixedBooleanIndicator.getValue(1)!!)
        Assert.assertTrue(fixedBooleanIndicator.getValue(2)!!)
        Assert.assertFalse(fixedBooleanIndicator.getValue(3)!!)
        Assert.assertTrue(fixedBooleanIndicator.getValue(4)!!)
    }
}
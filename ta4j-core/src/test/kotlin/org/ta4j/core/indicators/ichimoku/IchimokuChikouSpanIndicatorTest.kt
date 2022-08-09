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
package org.ta4j.core.indicators.ichimoku

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.IntStream

class IchimokuChikouSpanIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private fun bar(i: Int): Bar {
        return MockBar(i.toDouble()) { n: Number? -> this.numOf(n) }
    }

    private fun barSeries(count: Int): BarSeries {
        val bars = IntStream.range(0, count).boxed().map { i: Int -> bar(i) }.collect(Collectors.toList())
        return BaseBarSeries(bars)
    }

    @Test
    fun testCalculateWithDefaultParam() {
        val barSeries = barSeries(27)
        val indicator = IchimokuChikouSpanIndicator(barSeries)
        Assert.assertEquals(numOf(26), indicator[0])
        Assert.assertEquals(NaN.NaN, indicator.getValue(1))
        Assert.assertEquals(NaN.NaN, indicator.getValue(2))
        Assert.assertEquals(NaN.NaN, indicator.getValue(3))
        Assert.assertEquals(NaN.NaN, indicator.getValue(4))
        Assert.assertEquals(NaN.NaN, indicator.getValue(5))
        Assert.assertEquals(NaN.NaN, indicator.getValue(6))
        Assert.assertEquals(NaN.NaN, indicator.getValue(7))
        Assert.assertEquals(NaN.NaN, indicator.getValue(8))
        Assert.assertEquals(NaN.NaN, indicator.getValue(9))
        Assert.assertEquals(NaN.NaN, indicator.getValue(10))
        Assert.assertEquals(NaN.NaN, indicator.getValue(11))
        Assert.assertEquals(NaN.NaN, indicator.getValue(12))
        Assert.assertEquals(NaN.NaN, indicator.getValue(13))
        Assert.assertEquals(NaN.NaN, indicator.getValue(14))
        Assert.assertEquals(NaN.NaN, indicator.getValue(15))
        Assert.assertEquals(NaN.NaN, indicator.getValue(16))
        Assert.assertEquals(NaN.NaN, indicator.getValue(17))
        Assert.assertEquals(NaN.NaN, indicator.getValue(18))
        Assert.assertEquals(NaN.NaN, indicator.getValue(19))
        Assert.assertEquals(NaN.NaN, indicator.getValue(20))
        Assert.assertEquals(NaN.NaN, indicator.getValue(21))
        Assert.assertEquals(NaN.NaN, indicator.getValue(22))
        Assert.assertEquals(NaN.NaN, indicator.getValue(23))
        Assert.assertEquals(NaN.NaN, indicator.getValue(24))
        Assert.assertEquals(NaN.NaN, indicator.getValue(25))
        Assert.assertEquals(NaN.NaN, indicator.getValue(26))
    }

    @Test
    fun testCalculateWithSpecifiedValue() {
        val barSeries = barSeries(11)
        val indicator = IchimokuChikouSpanIndicator(barSeries, 3)
        Assert.assertEquals(numOf(3), indicator[0])
        Assert.assertEquals(numOf(4), indicator.getValue(1))
        Assert.assertEquals(numOf(5), indicator.getValue(2))
        Assert.assertEquals(numOf(6), indicator.getValue(3))
        Assert.assertEquals(numOf(7), indicator.getValue(4))
        Assert.assertEquals(numOf(8), indicator.getValue(5))
        Assert.assertEquals(numOf(9), indicator.getValue(6))
        Assert.assertEquals(numOf(10), indicator.getValue(7))
        Assert.assertEquals(NaN.NaN, indicator.getValue(8))
        Assert.assertEquals(NaN.NaN, indicator.getValue(9))
        Assert.assertEquals(NaN.NaN, indicator.getValue(10))
    }
}
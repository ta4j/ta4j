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
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.indicators.EMAIndicator
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.max
import kotlin.math.min

class PreviousValueIndicatorTest {
    private lateinit var prevValueIndicator: PreviousValueIndicator
    private lateinit var openPriceIndicator: OpenPriceIndicator
    private lateinit var lowPriceIndicator: LowPriceIndicator
    private lateinit var highPriceIndicator: HighPriceIndicator
    private lateinit var volumeIndicator: VolumeIndicator
    private lateinit var emaIndicator: EMAIndicator
    private lateinit var series: BarSeries
    @Before
    fun setUp() {
        val r = Random()
        series = BaseBarSeries("test")
        for (i in 0..999) {
            val open = r.nextDouble()
            val close = r.nextDouble()
            val max = max(close + r.nextDouble(), open + r.nextDouble())
            val min = min(0.0, min(close - r.nextDouble(), open - r.nextDouble()))
            val dateTime = ZonedDateTime.now().minusSeconds((1001 - i).toLong())
            series.addBar(dateTime, open, close, max, min, i)
        }
        openPriceIndicator = OpenPriceIndicator(series)
        lowPriceIndicator = LowPriceIndicator(series)
        highPriceIndicator = HighPriceIndicator(series)
        volumeIndicator = VolumeIndicator(series)
        val closePriceIndicator = ClosePriceIndicator(series)
        emaIndicator = EMAIndicator(closePriceIndicator, 20)
    }

    @Test
    fun shouldBePreviousValueFromIndicator() {

        // test 1 with openPrice-indicator
        prevValueIndicator = PreviousValueIndicator(openPriceIndicator)
        Assert.assertEquals(prevValueIndicator[0], openPriceIndicator[0])
        for (i in 1 until series.barCount) {
            Assert.assertEquals(prevValueIndicator[i], openPriceIndicator.getValue(i - 1))
        }

        // test 2 with lowPrice-indicator
        prevValueIndicator = PreviousValueIndicator(lowPriceIndicator)
        Assert.assertEquals(prevValueIndicator[0], lowPriceIndicator[0])
        for (i in 1 until series.barCount) {
            Assert.assertEquals(prevValueIndicator[i], lowPriceIndicator.getValue(i - 1))
        }

        // test 3 with highPrice-indicator
        prevValueIndicator = PreviousValueIndicator(highPriceIndicator)
        Assert.assertEquals(prevValueIndicator[0], highPriceIndicator[0])
        for (i in 1 until series.barCount) {
            Assert.assertEquals(prevValueIndicator[i], highPriceIndicator.getValue(i - 1))
        }
    }

    @Test
    fun shouldBeNthPreviousValueFromIndicator() {
        for (i in 1 until series.barCount) {
            testWithN(i)
        }
    }

    private fun testWithN(n: Int) {

        // test 1 with volume-indicator
        prevValueIndicator = PreviousValueIndicator(volumeIndicator, n)
        for (i in 0 until n) {
            Assert.assertEquals(prevValueIndicator[i], volumeIndicator[0])
        }
        for (i in n until series.barCount) {
            Assert.assertEquals(prevValueIndicator[i], volumeIndicator.getValue(i - n))
        }

        // test 2 with ema-indicator
        prevValueIndicator = PreviousValueIndicator(emaIndicator, n)
        for (i in 0 until n) {
            Assert.assertEquals(prevValueIndicator[i], emaIndicator[0])
        }
        for (i in n until series.barCount) {
            Assert.assertEquals(prevValueIndicator[i], emaIndicator.getValue(i - n))
        }
    }

    @Test
    fun testToStringMethodWithN1() {
        prevValueIndicator = PreviousValueIndicator(openPriceIndicator)
        val prevValueIndicatorAsString = prevValueIndicator.toString()
        Assert.assertTrue(prevValueIndicatorAsString.startsWith("PreviousValueIndicator["))
        Assert.assertTrue(prevValueIndicatorAsString.endsWith("]"))
    }

    @Test
    fun testToStringMethodWithNGreaterThen1() {
        prevValueIndicator = PreviousValueIndicator(openPriceIndicator, 2)
        val prevValueIndicatorAsString = prevValueIndicator.toString()
        Assert.assertTrue(prevValueIndicatorAsString.startsWith("PreviousValueIndicator(2)["))
        Assert.assertTrue(prevValueIndicatorAsString.endsWith("]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPreviousValueIndicatorWithNonPositiveN() {
        prevValueIndicator = PreviousValueIndicator(openPriceIndicator, 0)
    }
}
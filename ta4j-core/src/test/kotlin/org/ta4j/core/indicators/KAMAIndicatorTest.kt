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

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

/**
 * The Class KAMAIndicatorTest.
 *
 * @see [](http://stockcharts.com/school/data/media/chart_school/technical_indicators_and_overlays/kaufman_s_adaptive_moving_average/cs-kama.xls>
http://stockcharts.com/school/data/media/chart_school/technical_indicators_and_overlays/kaufman_s_adaptive_moving_average/cs-kama.xls</a>
) */
class KAMAIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var data: BarSeries? = null
    @Before
    fun setUp() {
        data = MockBarSeries(
            numFunction, 110.46, 109.80, 110.17, 109.82, 110.15, 109.31, 109.05, 107.94, 107.76,
            109.24, 109.40, 108.50, 107.96, 108.55, 108.85, 110.44, 109.89, 110.70, 110.79, 110.22, 110.00, 109.27,
            106.69, 107.07, 107.92, 107.95, 107.70, 107.97, 106.09, 106.03, 107.65, 109.54, 110.26, 110.38, 111.94,
            113.59, 113.98, 113.91, 112.62, 112.20, 111.10, 110.18, 111.13, 111.55, 112.08, 111.95, 111.60, 111.39,
            112.25
        )
    }

    @Test
    fun kama() {
        val closePrice = ClosePriceIndicator(data)
        val kama = KAMAIndicator(closePrice, 10, 2, 30)
        TestUtils.assertNumEquals(109.2400, kama.getValue(9))
        TestUtils.assertNumEquals(109.2449, kama.getValue(10))
        TestUtils.assertNumEquals(109.2165, kama.getValue(11))
        TestUtils.assertNumEquals(109.1173, kama.getValue(12))
        TestUtils.assertNumEquals(109.0981, kama.getValue(13))
        TestUtils.assertNumEquals(109.0894, kama.getValue(14))
        TestUtils.assertNumEquals(109.1240, kama.getValue(15))
        TestUtils.assertNumEquals(109.1376, kama.getValue(16))
        TestUtils.assertNumEquals(109.2769, kama.getValue(17))
        TestUtils.assertNumEquals(109.4365, kama.getValue(18))
        TestUtils.assertNumEquals(109.4569, kama.getValue(19))
        TestUtils.assertNumEquals(109.4651, kama.getValue(20))
        TestUtils.assertNumEquals(109.4612, kama.getValue(21))
        TestUtils.assertNumEquals(109.3904, kama.getValue(22))
        TestUtils.assertNumEquals(109.3165, kama.getValue(23))
        TestUtils.assertNumEquals(109.2924, kama.getValue(24))
        TestUtils.assertNumEquals(109.1836, kama.getValue(25))
        TestUtils.assertNumEquals(109.0778, kama.getValue(26))
        TestUtils.assertNumEquals(108.9498, kama.getValue(27))
        TestUtils.assertNumEquals(108.4230, kama.getValue(28))
        TestUtils.assertNumEquals(108.0157, kama.getValue(29))
        TestUtils.assertNumEquals(107.9967, kama.getValue(30))
        TestUtils.assertNumEquals(108.0069, kama.getValue(31))
        TestUtils.assertNumEquals(108.2596, kama.getValue(32))
        TestUtils.assertNumEquals(108.4818, kama.getValue(33))
        TestUtils.assertNumEquals(108.9119, kama.getValue(34))
        TestUtils.assertNumEquals(109.6734, kama.getValue(35))
        TestUtils.assertNumEquals(110.4947, kama.getValue(36))
        TestUtils.assertNumEquals(111.1077, kama.getValue(37))
        TestUtils.assertNumEquals(111.4622, kama.getValue(38))
        TestUtils.assertNumEquals(111.6092, kama.getValue(39))
        TestUtils.assertNumEquals(111.5663, kama.getValue(40))
        TestUtils.assertNumEquals(111.5491, kama.getValue(41))
        TestUtils.assertNumEquals(111.5425, kama.getValue(42))
        TestUtils.assertNumEquals(111.5426, kama.getValue(43))
        TestUtils.assertNumEquals(111.5457, kama.getValue(44))
        TestUtils.assertNumEquals(111.5658, kama.getValue(45))
        TestUtils.assertNumEquals(111.5688, kama.getValue(46))
        TestUtils.assertNumEquals(111.5522, kama.getValue(47))
        TestUtils.assertNumEquals(111.5595, kama.getValue(48))
    }

    @Test
    fun getValueOnDeepIndicesShouldNotCauseStackOverflow() {
        val series: BarSeries = MockBarSeries(numFunction)
        series.setMaximumBarCount(5000)
        Assert.assertEquals(5000, series.barCount.toLong())
        val kama = KAMAIndicator(ClosePriceIndicator(series), 10, 2, 30)
        try {
            TestUtils.assertNumEquals("2999.75", kama.getValue(3000))
        } catch (t: Throwable) {
            Assert.fail(t.message)
        }
    }
}
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
package org.ta4j.core.indicators.bollinger

import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class BollingerBandWidthIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var closePrice: ClosePriceIndicator? = null
    @Before
    fun setUp() {
        val data: BarSeries = MockBarSeries(
            numFunction,
            10.0,
            12.0,
            15.0,
            14.0,
            17.0,
            20.0,
            21.0,
            20.0,
            20.0,
            19.0,
            20.0,
            17.0,
            12.0,
            12.0,
            9.0,
            8.0,
            9.0,
            10.0,
            9.0,
            10.0
        )
        closePrice = ClosePriceIndicator(data)
    }

    @Test
    fun bollingerBandWidthUsingSMAAndStandardDeviation() {
        val sma = SMAIndicator(closePrice!!, 5)
        val standardDeviation = StandardDeviationIndicator(closePrice!!, 5)
        val bbmSMA = BollingerBandsMiddleIndicator(sma)
        val bbuSMA = BollingerBandsUpperIndicator(bbmSMA, standardDeviation)
        val bblSMA = BollingerBandsLowerIndicator(bbmSMA, standardDeviation)
        val bandwidth = BollingerBandWidthIndicator(bbuSMA, bbmSMA, bblSMA)
        TestUtils.assertNumEquals(0.0, bandwidth[0])
        TestUtils.assertNumEquals(36.3636, bandwidth.getValue(1))
        TestUtils.assertNumEquals(66.6423, bandwidth.getValue(2))
        TestUtils.assertNumEquals(60.2443, bandwidth.getValue(3))
        TestUtils.assertNumEquals(71.0767, bandwidth.getValue(4))
        TestUtils.assertNumEquals(69.9394, bandwidth.getValue(5))
        TestUtils.assertNumEquals(62.7043, bandwidth.getValue(6))
        TestUtils.assertNumEquals(56.0178, bandwidth.getValue(7))
        TestUtils.assertNumEquals(27.683, bandwidth.getValue(8))
        TestUtils.assertNumEquals(12.6491, bandwidth.getValue(9))
        TestUtils.assertNumEquals(12.6491, bandwidth.getValue(10))
        TestUtils.assertNumEquals(24.2956, bandwidth.getValue(11))
        TestUtils.assertNumEquals(68.3332, bandwidth.getValue(12))
        TestUtils.assertNumEquals(85.1469, bandwidth.getValue(13))
        TestUtils.assertNumEquals(112.8481, bandwidth.getValue(14))
        TestUtils.assertNumEquals(108.1682, bandwidth.getValue(15))
        TestUtils.assertNumEquals(66.9328, bandwidth.getValue(16))
        TestUtils.assertNumEquals(56.5194, bandwidth.getValue(17))
        TestUtils.assertNumEquals(28.1091, bandwidth.getValue(18))
        TestUtils.assertNumEquals(32.5362, bandwidth.getValue(19))
    }
}
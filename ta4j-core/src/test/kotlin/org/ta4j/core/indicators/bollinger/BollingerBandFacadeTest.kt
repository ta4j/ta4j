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

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.OpenPriceIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class BollingerBandFacadeTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun testCreation() {
        val data: BarSeries =
            MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0, 3.0, 4.0, 3.0, 2.0)
//        val closePriceIndicator = ClosePriceIndicator(data)
        val barCount = 3
        val bollingerBandFacade = BollingerBandFacade(data, barCount, 2)
        Assert.assertEquals(data, bollingerBandFacade.bandwidth().barSeries)
        Assert.assertEquals(data, bollingerBandFacade.middle().barSeries)
        val bollingerBandFacadeOfIndicator = BollingerBandFacade(
            OpenPriceIndicator(data),
            barCount, 2
        )
        Assert.assertEquals(data, bollingerBandFacadeOfIndicator.lower().barSeries)
        Assert.assertEquals(data, bollingerBandFacadeOfIndicator.upper().barSeries)
    }

    @Test
    fun testNumericFacadesSameAsDefaultIndicators() {
        val data: BarSeries =
            MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0, 3.0, 4.0, 3.0, 2.0)
        val closePriceIndicator = ClosePriceIndicator(data)
        val barCount = 3
        val sma: Indicator<Num> = SMAIndicator(closePriceIndicator, 3)
        val middleBB = BollingerBandsMiddleIndicator(sma)
        val standardDeviation = StandardDeviationIndicator(
            closePriceIndicator,
            barCount
        )
        val lowerBB = BollingerBandsLowerIndicator(middleBB, standardDeviation)
        val upperBB = BollingerBandsUpperIndicator(middleBB, standardDeviation)
        val pcb = PercentBIndicator(ClosePriceIndicator(data), 5, 2.0)
        val widthBB = BollingerBandWidthIndicator(upperBB, middleBB, lowerBB)
        val bollingerBandFacade = BollingerBandFacade(data, barCount, 2)
        val middleBBNumeric = bollingerBandFacade.middle()
        val lowerBBNumeric = bollingerBandFacade.lower()
        val upperBBNumeric = bollingerBandFacade.upper()
        val widthBBNumeric = bollingerBandFacade.bandwidth()
        val pcbNumeric = BollingerBandFacade(data, 5, 2).percentB()
        for (i in data.beginIndex..data.endIndex) {
            TestUtils.assertNumEquals(pcb[i], pcbNumeric[i])
            TestUtils.assertNumEquals(lowerBB[i], lowerBBNumeric[i])
            TestUtils.assertNumEquals(middleBB[i], middleBBNumeric[i])
            TestUtils.assertNumEquals(upperBB[i], upperBBNumeric[i])
            TestUtils.assertNumEquals(widthBB[i], widthBBNumeric[i])
        }
    }
}
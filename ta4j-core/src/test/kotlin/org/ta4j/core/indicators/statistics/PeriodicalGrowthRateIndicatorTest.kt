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
package org.ta4j.core.indicators.statistics

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import org.ta4j.core.rules.CrossedDownIndicatorRule
import org.ta4j.core.rules.CrossedUpIndicatorRule
import java.util.function.Function

class PeriodicalGrowthRateIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var seriesManager: BarSeriesManager? = null
    private var closePrice: ClosePriceIndicator? = null
    @Before
    fun setUp() {
        val mockSeries: BarSeries = MockBarSeries(
            numFunction, 29.49, 28.30, 27.74, 27.65, 27.60, 28.70, 28.60, 28.19,
            27.40, 27.20, 27.28, 27.00, 27.59, 26.20, 25.75, 24.75, 23.33, 24.45, 24.25, 25.02, 23.60, 24.20, 24.28,
            25.70, 25.46, 25.10, 25.00, 25.00, 25.85
        )
        seriesManager = BarSeriesManager(mockSeries)
        closePrice = ClosePriceIndicator(mockSeries)
    }

    @Test
    fun testGetTotalReturn() {
        val gri = PeriodicalGrowthRateIndicator(closePrice!!, 5)
        val result = gri.totalReturn
        TestUtils.assertNumEquals(0.9564, result)
    }

    @Test
    fun testCalculation() {
        val gri = PeriodicalGrowthRateIndicator(closePrice!!, 5)
        Assert.assertEquals(gri[0], NaN.NaN)
        Assert.assertEquals(gri.getValue(4), NaN.NaN)
        TestUtils.assertNumEquals(-0.0268, gri.getValue(5))
        TestUtils.assertNumEquals(0.0541, gri.getValue(6))
        TestUtils.assertNumEquals(-0.0495, gri.getValue(10))
        TestUtils.assertNumEquals(0.2009, gri.getValue(21))
        TestUtils.assertNumEquals(0.0220, gri.getValue(24))
        Assert.assertEquals(gri.getValue(25), NaN.NaN)
        Assert.assertEquals(gri.getValue(26), NaN.NaN)
    }

    @Test
    fun testStrategies() {
        val gri = PeriodicalGrowthRateIndicator(closePrice!!, 5)

        // Rules
        val buyingRule: Rule = CrossedUpIndicatorRule(gri, 0)
        val sellingRule: Rule = CrossedDownIndicatorRule(gri, 0)
        val strategy: Strategy = BaseStrategy(buyingRule, sellingRule)

        // Check positions
        val result = seriesManager!!.run(strategy).getPositionCount()
        val expResult = 3
        Assert.assertEquals(expResult.toLong(), result.toLong())
    }
}
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
import org.ta4j.core.*
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.ConstantIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import org.ta4j.core.rules.OverIndicatorRule
import org.ta4j.core.rules.UnderIndicatorRule
import java.util.*
import java.util.function.Function

class CachedIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var series: BarSeries? = null
    @Before
    fun setUp() {
        series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 3.0, 4.0, 5.0, 4.0, 3.0, 3.0, 4.0, 3.0, 2.0)
    }

    @Test
    fun ifCacheWorks() {
        val sma = SMAIndicator(ClosePriceIndicator(series), 3)
        val firstTime = sma.getValue(4)
        val secondTime = sma.getValue(4)
        Assert.assertEquals(firstTime, secondTime)
    }

    @Test // should be not null
    fun getValueWithNullBarSeries() {
        val constant = ConstantIndicator(
            BaseBarSeriesBuilder().withNumTypeOf(numFunction).build(), numFunction.apply(10)
        )
        Assert.assertEquals(numFunction.apply(10), constant[0])
        Assert.assertEquals(numFunction.apply(10), constant.getValue(100))
        Assert.assertNotNull(constant.barSeries)
        val sma = SMAIndicator(constant, 10)
        Assert.assertEquals(numFunction.apply(10), sma[0])
        Assert.assertEquals(numFunction.apply(10), sma.getValue(100))
        Assert.assertNotNull(sma.barSeries)
    }

    @Test
    fun getValueWithCacheLengthIncrease() {
        val data = DoubleArray(200)
        Arrays.fill(data, 10.0)
        val sma = SMAIndicator(ClosePriceIndicator(MockBarSeries(numFunction, *data)), 100)
        TestUtils.assertNumEquals(10, sma.getValue(105))
    }

    @Test
    fun getValueWithOldResultsRemoval() {
        val data = DoubleArray(20)
        Arrays.fill(data, 1.0)
        val barSeries: BarSeries = MockBarSeries(numFunction, *data)
        val sma = SMAIndicator(ClosePriceIndicator(barSeries), 10)
        TestUtils.assertNumEquals(1, sma.getValue(5))
        TestUtils.assertNumEquals(1, sma.getValue(10))
        barSeries.setMaximumBarCount(12)
        TestUtils.assertNumEquals(1, sma.getValue(19))
    }

    @Test
    fun strategyExecutionOnCachedIndicatorAndLimitedBarSeries() {
        val barSeries: BarSeries = MockBarSeries(numFunction, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)
        val sma = SMAIndicator(ClosePriceIndicator(barSeries), 2)
        // Theoretical values for SMA(2) cache: 0, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5
        barSeries.setMaximumBarCount(6)
        // Theoretical values for SMA(2) cache: null, null, 2, 2.5, 3.5, 4.5, 5.5, 6.5
        val strategy: Strategy = BaseStrategy(
            OverIndicatorRule(sma, sma.numOf(3)),
            UnderIndicatorRule(sma, sma.numOf(3))
        )
        // Theoretical shouldEnter results: false, false, false, false, true, true,
        // true, true
        // Theoretical shouldExit results: false, false, true, true, false, false,
        // false, false

        // As we return the first bar/result found for the removed bars:
        // -> Approximated values for ClosePrice cache: 2, 2, 2, 3, 4, 5, 6, 7
        // -> Approximated values for SMA(2) cache: 2, 2, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        // Then enters/exits are also approximated:
        // -> shouldEnter results: false, false, false, false, true, true, true, true
        // -> shouldExit results: true, true, true, true, false, false, false, false
        Assert.assertFalse(strategy.shouldEnter(0))
        Assert.assertTrue(strategy.shouldExit(0))
        Assert.assertFalse(strategy.shouldEnter(1))
        Assert.assertTrue(strategy.shouldExit(1))
        Assert.assertFalse(strategy.shouldEnter(2))
        Assert.assertTrue(strategy.shouldExit(2))
        Assert.assertFalse(strategy.shouldEnter(3))
        Assert.assertTrue(strategy.shouldExit(3))
        Assert.assertTrue(strategy.shouldEnter(4))
        Assert.assertFalse(strategy.shouldExit(4))
        Assert.assertTrue(strategy.shouldEnter(5))
        Assert.assertFalse(strategy.shouldExit(5))
        Assert.assertTrue(strategy.shouldEnter(6))
        Assert.assertFalse(strategy.shouldExit(6))
        Assert.assertTrue(strategy.shouldEnter(7))
        Assert.assertFalse(strategy.shouldExit(7))
    }

    @Test
    fun getValueOnResultsCalculatedFromRemovedBarsShouldReturnFirstRemainingResult() {
        val barSeries: BarSeries = MockBarSeries(numFunction, 1.0, 1.0, 1.0, 1.0, 1.0)
        barSeries.setMaximumBarCount(3)
        Assert.assertEquals(2, barSeries.removedBarsCount.toLong())
        val sma = SMAIndicator(ClosePriceIndicator(barSeries), 2)
        for (i in 0..4) {
            TestUtils.assertNumEquals(1, sma[i])
        }
    }

    @Test
    fun recursiveCachedIndicatorOnMovingBarSeriesShouldNotCauseStackOverflow() {
        // Added to check issue #120: https://github.com/mdeverdelhan/ta4j/issues/120
        // See also: CachedIndicator#getValue(int index)
        series = MockBarSeries(numFunction)
        series!!.setMaximumBarCount(5)
        Assert.assertEquals(5, series!!.barCount.toLong())
        val zlema = ZLEMAIndicator(ClosePriceIndicator(series), 1)
        try {
            TestUtils.assertNumEquals(4996, zlema.getValue(8))
        } catch (t: Throwable) {
            Assert.fail(t.message)
        }
    }

    @Test
    fun leaveLastBarUncached() {
        val barSeries: BarSeries = MockBarSeries(numFunction)
        val closePrice = ClosePriceIndicator(barSeries)
        TestUtils.assertNumEquals(5000, closePrice.getValue(barSeries.endIndex))
        barSeries.lastBar.addTrade(numOf(10), numOf(5))
        TestUtils.assertNumEquals(5, closePrice.getValue(barSeries.endIndex))
    }
}
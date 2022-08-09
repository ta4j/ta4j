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
package org.ta4j.core.rules

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class StopGainRuleTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private var closePrice: ClosePriceIndicator? = null
    @Before
    fun setUp() {
        closePrice = ClosePriceIndicator(
            MockBarSeries(numFunction, 100.0, 105.0, 110.0, 120.0, 150.0, 120.0, 160.0, 180.0, 170.0, 135.0, 104.0)
        )
    }

    // 30% stop-gain
    @Test
    fun isSatisfiedWorksForBuy() {
            val tradingRecord: TradingRecord = BaseTradingRecord(TradeType.BUY)
            val tradedAmount = numOf(1)

            // 30% stop-gain
            val rule = StopGainRule(closePrice!!, numOf(30))
            Assert.assertFalse(rule.isSatisfied(0, null))
            Assert.assertFalse(rule.isSatisfied(1, tradingRecord))

            // Enter at 108
            tradingRecord.enter(2, numOf(108), tradedAmount)
            Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
            Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
            Assert.assertTrue(rule.isSatisfied(4, tradingRecord))
            // Exit
            tradingRecord.exit(5)

            // Enter at 118
            tradingRecord.enter(5, numOf(118), tradedAmount)
            Assert.assertFalse(rule.isSatisfied(5, tradingRecord))
            Assert.assertTrue(rule.isSatisfied(6, tradingRecord))
            Assert.assertTrue(rule.isSatisfied(7, tradingRecord))
        }

    // 30% stop-gain
    @Test
    fun isSatisfiedWorksForSell() {
            val tradingRecord: TradingRecord = BaseTradingRecord(TradeType.SELL)
            val tradedAmount = numOf(1)

            // 30% stop-gain
            val rule = StopGainRule(closePrice!!, numOf(10))
            Assert.assertFalse(rule.isSatisfied(0, null))
            Assert.assertFalse(rule.isSatisfied(1, tradingRecord))

            // Enter at 178
            tradingRecord.enter(7, numOf(178), tradedAmount)
            Assert.assertFalse(rule.isSatisfied(7, tradingRecord))
            Assert.assertFalse(rule.isSatisfied(8, tradingRecord))
            Assert.assertTrue(rule.isSatisfied(9, tradingRecord))
            // Exit
            tradingRecord.exit(10)

            // Enter at 119
            tradingRecord.enter(3, numOf(119), tradedAmount)
            Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
            Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
            Assert.assertTrue(rule.isSatisfied(1, tradingRecord))
            Assert.assertTrue(rule.isSatisfied(10, tradingRecord))
        }
}
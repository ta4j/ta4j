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
/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class TrailingStopLossRuleTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Any?, Any?>(numFunction) {
    @Test
    fun isSatisfiedForBuy() {
        val tradingRecord = BaseTradingRecord(TradeType.BUY)
        val closePrice = ClosePriceIndicator(
            MockBarSeries(numFunction, 100.0, 110.0, 120.0, 130.0, 117.00, 130.0, 116.99)
        )

        // 10% trailing-stop-loss
        val rule = TrailingStopLossRule(closePrice, numOf(10))
        Assert.assertFalse(rule.isSatisfied(0, null))
        Assert.assertNull(rule.currentStopLossLimitActivation)
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))
        Assert.assertNull(rule.currentStopLossLimitActivation)

        // Enter at 114
        tradingRecord.enter(2, numOf(114), numOf(1))
        Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
        Assert.assertEquals(numOf(120).times(numOf(0.9)), rule.currentStopLossLimitActivation)
        Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
        Assert.assertEquals(numOf(130).times(numOf(0.9)), rule.currentStopLossLimitActivation)
        Assert.assertTrue(rule.isSatisfied(4, tradingRecord))
        Assert.assertEquals(numOf(130).times(numOf(0.9)), rule.currentStopLossLimitActivation)
        // Exit
        tradingRecord.exit(5)

        // Enter at 128
        tradingRecord.enter(5, numOf(128), numOf(1))
        Assert.assertFalse(rule.isSatisfied(5, tradingRecord))
        Assert.assertEquals(numOf(130).times(numOf(0.9)), rule.currentStopLossLimitActivation)
        Assert.assertTrue(rule.isSatisfied(6, tradingRecord))
        Assert.assertEquals(numOf(130).times(numOf(0.9)), rule.currentStopLossLimitActivation)
    }

    @Test
    fun isSatisfiedForBuyForBarCount() {
        val tradingRecord = BaseTradingRecord(TradeType.BUY)
        val closePrice = ClosePriceIndicator(
            MockBarSeries(numFunction, 100.0, 110.0, 120.0, 130.0, 120.0, 117.00, 117.00, 130.0, 116.99)
        )

        // 10% trailing-stop-loss
        val rule = TrailingStopLossRule(closePrice, numOf(10), 3)
        Assert.assertFalse(rule.isSatisfied(0, null))
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))

        // Enter at 114
        tradingRecord.enter(2, numOf(114), numOf(1))
        Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(4, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(5, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(6, tradingRecord))
        // Exit
        tradingRecord.exit(7)

        // Enter at 128
        tradingRecord.enter(7, numOf(128), numOf(1))
        Assert.assertFalse(rule.isSatisfied(7, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(8, tradingRecord))
    }

    @Test
    fun isSatisfiedForSell() {
        val tradingRecord = BaseTradingRecord(TradeType.SELL)
        val closePrice = ClosePriceIndicator(
            MockBarSeries(numFunction, 100.0, 90.0, 80.0, 70.0, 77.00, 120.0, 132.01)
        )

        // 10% trailing-stop-loss
        val rule = TrailingStopLossRule(closePrice, numOf(10))
        Assert.assertFalse(rule.isSatisfied(0, null))
        Assert.assertNull(rule.currentStopLossLimitActivation)
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))
        Assert.assertNull(rule.currentStopLossLimitActivation)

        // Enter at 84
        tradingRecord.enter(2, numOf(84), numOf(1))
        Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
        Assert.assertEquals(numOf(80).times(numOf(1.1)), rule.currentStopLossLimitActivation)
        Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
        Assert.assertEquals(numOf(70).times(numOf(1.1)), rule.currentStopLossLimitActivation)
        Assert.assertTrue(rule.isSatisfied(4, tradingRecord))
        Assert.assertEquals(numOf(70).times(numOf(1.1)), rule.currentStopLossLimitActivation)
        // Exit
        tradingRecord.exit(5)

        // Enter at 128
        tradingRecord.enter(5, numOf(128), numOf(1))
        Assert.assertFalse(rule.isSatisfied(5, tradingRecord))
        Assert.assertEquals(numOf(120).times(numOf(1.1)), rule.currentStopLossLimitActivation)
        Assert.assertTrue(rule.isSatisfied(6, tradingRecord))
        Assert.assertEquals(numOf(120).times(numOf(1.1)), rule.currentStopLossLimitActivation)
    }

    @Test
    fun isSatisfiedForSellForBarCount() {
        val tradingRecord = BaseTradingRecord(TradeType.SELL)
        val closePrice = ClosePriceIndicator(
            MockBarSeries(numFunction, 100.0, 90.0, 80.0, 70.0, 70.0, 73.0, 77.00, 90.0, 120.0, 132.01)
        )

        // 10% trailing-stop-loss and 2 bars back
        val rule = TrailingStopLossRule(closePrice, numOf(10), 3)
        Assert.assertFalse(rule.isSatisfied(0, null))
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))

        // Enter at 84
        tradingRecord.enter(2, numOf(84), numOf(1))
        Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(4, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(5, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(6, tradingRecord))
        // Exit
        tradingRecord.exit(7)

        // Enter at 128
        tradingRecord.enter(7, numOf(91), numOf(1))
        Assert.assertFalse(rule.isSatisfied(7, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(8, tradingRecord))
    }
}
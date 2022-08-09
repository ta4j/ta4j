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
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord

class WaitForRuleTest {
    private var tradingRecord: TradingRecord? = null
    private var rule: WaitForRule? = null
    @Before
    fun setUp() {
        tradingRecord = BaseTradingRecord()
    }

    @Test
    fun waitForSinceLastBuyRuleIsSatisfied() {
        // Waits for 3 bars since last buy trade
        rule = WaitForRule(TradeType.BUY, 3)
        Assert.assertFalse(rule!!.isSatisfied(0, null))
        Assert.assertFalse(rule!!.isSatisfied(1, tradingRecord))
        tradingRecord!!.enter(10)
        Assert.assertFalse(rule!!.isSatisfied(10, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(11, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(12, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(13, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(14, tradingRecord))
        tradingRecord!!.exit(15)
        Assert.assertTrue(rule!!.isSatisfied(15, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(16, tradingRecord))
        tradingRecord!!.enter(17)
        Assert.assertFalse(rule!!.isSatisfied(17, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(18, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(19, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(20, tradingRecord))
    }

    @Test
    fun waitForSinceLastSellRuleIsSatisfied() {
        // Waits for 2 bars since last sell trade
        rule = WaitForRule(TradeType.SELL, 2)
        Assert.assertFalse(rule!!.isSatisfied(0, null))
        Assert.assertFalse(rule!!.isSatisfied(1, tradingRecord))
        tradingRecord!!.enter(10)
        Assert.assertFalse(rule!!.isSatisfied(10, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(11, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(12, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(13, tradingRecord))
        tradingRecord!!.exit(15)
        Assert.assertFalse(rule!!.isSatisfied(15, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(16, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(17, tradingRecord))
        tradingRecord!!.enter(17)
        Assert.assertTrue(rule!!.isSatisfied(17, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(18, tradingRecord))
        tradingRecord!!.exit(20)
        Assert.assertFalse(rule!!.isSatisfied(20, tradingRecord))
        Assert.assertFalse(rule!!.isSatisfied(21, tradingRecord))
        Assert.assertTrue(rule!!.isSatisfied(22, tradingRecord))
    }
}
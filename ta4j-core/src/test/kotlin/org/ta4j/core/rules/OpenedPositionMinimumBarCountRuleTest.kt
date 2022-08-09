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
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.TradingRecord
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.Num
import java.util.function.Function

class OpenedPositionMinimumBarCountRuleTest {
    @Test(expected = IllegalArgumentException::class)
    fun testAtLeastBarCountRuleForNegativeNumberShouldThrowException() {
        OpenedPositionMinimumBarCountRule(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAtLeastBarCountRuleForZeroShouldThrowException() {
        OpenedPositionMinimumBarCountRule(0)
    }

    @Test
    fun testAtLeastOneBarRuleForOpenedTrade() {
        val rule = OpenedPositionMinimumBarCountRule(1)
        val series: BarSeries = MockBarSeries(
            Function<Number?, Num> { obj: Number? -> DecimalNum.Companion.valueOf(obj) },
            1.0,
            2.0,
            3.0,
            4.0
        )
        val tradingRecord: TradingRecord = BaseTradingRecord(buyAt(0, series))
        Assert.assertFalse(rule.isSatisfied(0, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(1, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(2, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(3, tradingRecord))
    }

    @Test
    fun testAtLeastMoreThanOneBarRuleForOpenedTrade() {
        val rule = OpenedPositionMinimumBarCountRule(2)
        val series: BarSeries = MockBarSeries(
            Function<Number?, Num> { obj: Number? -> DecimalNum.Companion.valueOf(obj) },
            1.0,
            2.0,
            3.0,
            4.0
        )
        val tradingRecord: TradingRecord = BaseTradingRecord(buyAt(0, series))
        Assert.assertFalse(rule.isSatisfied(0, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(2, tradingRecord))
        Assert.assertTrue(rule.isSatisfied(3, tradingRecord))
    }

    @Test
    fun testAtLeastBarCountRuleForClosedTradeShouldAlwaysReturnsFalse() {
        val rule = OpenedPositionMinimumBarCountRule(1)
        val series: BarSeries = MockBarSeries(
            Function<Number?, Num> { obj: Number? -> DecimalNum.Companion.valueOf(obj) },
            1.0,
            2.0,
            3.0,
            4.0
        )
        val tradingRecord: TradingRecord = BaseTradingRecord(buyAt(0, series), sellAt(1, series))
        Assert.assertFalse(rule.isSatisfied(0, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
    }

    @Test
    fun testAtLeastBarCountRuleForEmptyTradingRecordShouldAlwaysReturnsFalse() {
        val rule = OpenedPositionMinimumBarCountRule(1)
        val tradingRecord: TradingRecord = BaseTradingRecord()
        Assert.assertFalse(rule.isSatisfied(0, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(1, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(2, tradingRecord))
        Assert.assertFalse(rule.isSatisfied(3, tradingRecord))
    }
}
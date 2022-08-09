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
package org.ta4j.core.criteria

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class EnterAndHoldReturnCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { params: Array<out Any?> ->
        if (params.size == 0) EnterAndHoldReturnCriterion() else EnterAndHoldReturnCriterion(
            (params[0] as TradeType?)!!
        )
    }, numFunction
) {
    @Test
    fun calculateOnlyWithGainPositions() {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 110.0, 100.0, 95.0, 105.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(2, series),
            buyAt(3, series), sellAt(5, series)
        )
        val buyAndHold = getCriterion()
        TestUtils.assertNumEquals(1.05, buyAndHold!!.calculate(series, tradingRecord))
        val sellAndHold = getCriterion(TradeType.SELL)
        TestUtils.assertNumEquals(0.95, sellAndHold!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateOnlyWithLossPositions() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(5, series)
        )
        val buyAndHold = getCriterion()
        TestUtils.assertNumEquals(0.7, buyAndHold!!.calculate(series, tradingRecord))
        val sellAndHold = getCriterion(TradeType.SELL)
        TestUtils.assertNumEquals(1.3, sellAndHold!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithNoPositions() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val buyAndHold = getCriterion()
        TestUtils.assertNumEquals(0.7, buyAndHold!!.calculate(series, BaseTradingRecord()))
        val sellAndHold = getCriterion(TradeType.SELL)
        TestUtils.assertNumEquals(1.3, sellAndHold!!.calculate(series, BaseTradingRecord()))
    }

    @Test
    fun calculateWithOnePositions() {
        val series = MockBarSeries(numFunction, 100.0, 105.0)
        val position = Position(buyAt(0, series), sellAt(1, series))
        val buyAndHold = getCriterion()
        TestUtils.assertNumEquals(105.0 / 100, buyAndHold!!.calculate(series, position))
        val sellAndHold = getCriterion(TradeType.SELL)
        TestUtils.assertNumEquals(0.95, sellAndHold!!.calculate(series, position))
    }

    @Test
    fun betterThan() {
        val buyAndHold = getCriterion()
        Assert.assertTrue(buyAndHold!!.betterThan(numOf(1.3)!!, numOf(1.1)!!))
        Assert.assertFalse(buyAndHold.betterThan(numOf(0.6)!!, numOf(0.9)!!))
        val sellAndHold = getCriterion(TradeType.SELL)
        Assert.assertTrue(sellAndHold!!.betterThan(numOf(1.3)!!, numOf(1.1)!!))
        Assert.assertFalse(sellAndHold.betterThan(numOf(0.6)!!, numOf(0.9)!!))
    }
}
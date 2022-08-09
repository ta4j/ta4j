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
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.CriterionFactory
import org.ta4j.core.TestUtils
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.TradingRecord
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class ExpectancyCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { ExpectancyCriterion() }, numFunction
) {
    @Test
    fun calculateOnlyWithProfitPositions() {
        val series = MockBarSeries(numFunction, 100.0, 110.0, 120.0, 130.0, 150.0, 160.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(2, series),
            buyAt(3, series), sellAt(5, series)
        )
        val avgLoss = getCriterion()
        TestUtils.assertNumEquals(0, avgLoss!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithMixedPositions() {
        val series = MockBarSeries(numFunction, 100.0, 110.0, 80.0, 130.0, 150.0, 160.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(2, series),
            buyAt(3, series), sellAt(5, series)
        )
        val avgLoss = getCriterion()
        TestUtils.assertNumEquals(-1.25, avgLoss!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateOnlyWithLossPositions() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 80.0, 70.0, 60.0, 50.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(5, series)
        )
        val avgLoss = getCriterion()
        TestUtils.assertNumEquals(0, avgLoss!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateProfitWithShortPositions() {
        val series = MockBarSeries(numFunction, 160.0, 140.0, 120.0, 100.0, 80.0, 60.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(0, series), buyAt(1, series),
            sellAt(2, series), buyAt(5, series)
        )
        val avgLoss = getCriterion()
        TestUtils.assertNumEquals(0, avgLoss!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateProfitWithMixedShortPositions() {
        val series = MockBarSeries(numFunction, 160.0, 200.0, 120.0, 100.0, 80.0, 60.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(0, series), buyAt(1, series),
            sellAt(2, series), buyAt(5, series)
        )
        val avgLoss = getCriterion()
        TestUtils.assertNumEquals(-1.25, avgLoss!!.calculate(series, tradingRecord))
    }

    @Test
    fun betterThan() {
        val criterion = getCriterion()
        Assert.assertTrue(criterion!!.betterThan(numOf(2.0)!!, numOf(1.5)!!))
        Assert.assertFalse(criterion.betterThan(numOf(1.5)!!, numOf(2.0)!!))
    }

    @Test
    fun testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction, getCriterion(), 0)
    }
}
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
import org.ta4j.core.criteria.pnl.GrossReturnCriterion
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.util.function.Function

class VersusBuyAndHoldCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { params: Array<out Any?> -> VersusBuyAndHoldCriterion((params[0] as AnalysisCriterion?)!!) },
    numFunction
) {
    @Test
    fun calculateOnlyWithGainPositions() {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 110.0, 100.0, 95.0, 105.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(2, series),
            buyAt(3, series), sellAt(5, series)
        )
        val buyAndHold = getCriterion(GrossReturnCriterion())
        TestUtils.assertNumEquals(1.10 * 1.05 / 1.05, buyAndHold!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateOnlyWithLossPositions() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(5, series)
        )
        val buyAndHold = getCriterion(GrossReturnCriterion())
        TestUtils.assertNumEquals(0.95 * 0.7 / 0.7, buyAndHold!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithOnlyOnePosition() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val position = Position(buyAt(0, series), sellAt(1, series))
        val buyAndHold = getCriterion(GrossReturnCriterion())
        TestUtils.assertNumEquals(100.0 / 70 / (100.0 / 95), buyAndHold!!.calculate(series, position))
    }

    @Test
    fun calculateWithNoPositions() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val buyAndHold = getCriterion(GrossReturnCriterion())
        TestUtils.assertNumEquals(1 / 0.7, buyAndHold!!.calculate(series, BaseTradingRecord()))
    }

    @Test
    fun calculateWithAverageProfit() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 130.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, NaN.NaN, NaN.NaN), sellAt(1, NaN.NaN, NaN.NaN),
            buyAt(2, NaN.NaN, NaN.NaN), sellAt(5, NaN.NaN, NaN.NaN)
        )
        val buyAndHold = getCriterion(AverageReturnPerBarCriterion())
        TestUtils.assertNumEquals(
            Math.pow(95.0 / 100 * 130.0 / 100, 1.0 / 6) / Math.pow(130.0 / 100, 1.0 / 6),
            buyAndHold!!.calculate(series, tradingRecord)
        )
    }

    @Test
    fun calculateWithNumberOfBars() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 130.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(5, series)
        )
        val buyAndHold = getCriterion(NumberOfBarsCriterion())
        TestUtils.assertNumEquals(6.0 / 6.0, buyAndHold!!.calculate(series, tradingRecord))
    }

    @Test
    fun betterThan() {
        val criterion = getCriterion(GrossReturnCriterion())
        Assert.assertTrue(criterion!!.betterThan(numOf(2.0)!!, numOf(1.5)!!))
        Assert.assertFalse(criterion.betterThan(numOf(1.5)!!, numOf(2.0)!!))
    }
}
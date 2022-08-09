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
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class WinningPositionsRatioCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { WinningPositionsRatioCriterion() }, numFunction
) {
    @Test
    fun calculate() {
        val series: BarSeries = MockBarSeries(numFunction, 100.0, 95.0, 102.0, 105.0, 97.0, 113.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(3, series), buyAt(4, series), sellAt(5, series)
        )
        val average = getCriterion()
        TestUtils.assertNumEquals(2.0 / 3, average!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithShortPositions() {
        val series: BarSeries = MockBarSeries(numFunction, 100.0, 95.0, 102.0, 105.0, 97.0, 113.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(0, series), buyAt(2, series),
            sellAt(3, series), buyAt(4, series)
        )
        val average = getCriterion()
        TestUtils.assertNumEquals(0.5, average!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithOnePosition() {
        val series: BarSeries = MockBarSeries(numFunction, 100.0, 95.0, 102.0, 105.0, 97.0, 113.0)
        var position = Position(buyAt(0, series), sellAt(1, series))
        val average = getCriterion()
        TestUtils.assertNumEquals(numOf(0), average!!.calculate(series, position))
        position = Position(buyAt(1, series), sellAt(2, series))
        TestUtils.assertNumEquals(1, average.calculate(series, position))
    }

    @Test
    fun betterThan() {
        val criterion = getCriterion()
        Assert.assertTrue(criterion!!.betterThan(numOf(12)!!, numOf(8)!!))
        Assert.assertFalse(criterion.betterThan(numOf(8)!!, numOf(12)!!))
    }

    @Test
    fun testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction, getCriterion(), 0)
    }
}
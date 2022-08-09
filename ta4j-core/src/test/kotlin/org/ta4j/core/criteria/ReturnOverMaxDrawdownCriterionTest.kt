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
import org.junit.Before
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.util.function.Function

class ReturnOverMaxDrawdownCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { ReturnOverMaxDrawdownCriterion() }, numFunction
) {
    private var rrc: AnalysisCriterion? = null
    @Before
    fun setUp() {
        rrc = getCriterion()
    }

    @Test
    fun rewardRiskRatioCriterion() {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 95.0, 100.0, 90.0, 95.0, 80.0, 120.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(4, series), buyAt(5, series), sellAt(7, series)
        )
        val totalProfit = 105.0 / 100 * (90.0 / 95.0) * (120.0 / 95)
        val peak = 105.0 / 100 * (100.0 / 95)
        val low = 105.0 / 100 * (90.0 / 95) * (80.0 / 95)
        TestUtils.assertNumEquals(totalProfit / ((peak - low) / peak), rrc!!.calculate(series, tradingRecord))
    }

    @Test
    fun rewardRiskRatioCriterionOnlyWithGain() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 6.0, 8.0, 20.0, 3.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(5, series)
        )
        Assert.assertTrue(rrc!!.calculate(series, tradingRecord).isNaN)
    }

    @Test
    fun rewardRiskRatioCriterionWithNoPositions() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 6.0, 8.0, 20.0, 3.0)
        Assert.assertTrue(rrc!!.calculate(series, BaseTradingRecord()).isNaN)
    }

    @Test
    fun withOnePosition() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 95.0, 100.0, 90.0, 95.0, 80.0, 120.0)
        val position = Position(buyAt(0, series), sellAt(1, series))
        val ratioCriterion = getCriterion()
        TestUtils.assertNumEquals(95.0 / 100 / (1.0 - 0.95), ratioCriterion!!.calculate(series, position))
    }

    @Test
    fun betterThan() {
        val criterion = getCriterion()
        Assert.assertTrue(criterion!!.betterThan(numOf(3.5)!!, numOf(2.2)!!))
        Assert.assertFalse(criterion.betterThan(numOf(1.5)!!, numOf(2.7)!!))
    }

    @Test
    fun testNoDrawDownForTradingRecord() {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 95.0, 100.0, 90.0, 95.0, 80.0, 120.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(3, series)
        )
        val result = rrc!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(NaN.NaN, result)
    }

    @Test
    fun testNoDrawDownForPosition() {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 95.0, 100.0, 90.0, 95.0, 80.0, 120.0)
        val position = Position(buyAt(0, series), sellAt(1, series))
        val result = rrc!!.calculate(series, position)
        TestUtils.assertNumEquals(NaN.NaN, result)
    }
}
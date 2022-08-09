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

class MaximumDrawdownCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { MaximumDrawdownCriterion() }, numFunction
) {
    @Test
    fun calculateWithNoTrades() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 6.0, 5.0, 20.0, 3.0)
        val mdd = getCriterion()
        TestUtils.assertNumEquals(0.0, mdd!!.calculate(series, BaseTradingRecord()))
    }

    @Test
    fun calculateWithOnlyGains() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 6.0, 8.0, 20.0, 3.0)
        val mdd = getCriterion()
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(2, series), sellAt(5, series)
        )
        TestUtils.assertNumEquals(0.0, mdd!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithGainsAndLosses() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 6.0, 5.0, 20.0, 3.0)
        val mdd = getCriterion()
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(3, series), sellAt(4, series), buyAt(5, series), sellAt(6, series)
        )
        TestUtils.assertNumEquals(.875, mdd!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithNullSeriesSizeShouldReturn0() {
        val series = MockBarSeries(numFunction, *doubleArrayOf())
        val mdd = getCriterion()
        TestUtils.assertNumEquals(0.0, mdd!!.calculate(series, BaseTradingRecord()))
    }

    @Test
    fun withTradesThatSellBeforeBuying() {
        val series = MockBarSeries(numFunction, 2.0, 1.0, 3.0, 5.0, 6.0, 3.0, 20.0)
        val mdd = getCriterion()
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(3, series), sellAt(4, series), sellAt(5, series), buyAt(6, series)
        )
        TestUtils.assertNumEquals(3.8, mdd!!.calculate(series, tradingRecord))
    }

    @Test
    fun withSimpleTrades() {
        val series = MockBarSeries(numFunction, 1.0, 10.0, 5.0, 6.0, 1.0)
        val mdd = getCriterion()
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(1, series),
            buyAt(1, series), sellAt(2, series), buyAt(2, series), sellAt(3, series),
            buyAt(3, series), sellAt(4, series)
        )
        TestUtils.assertNumEquals(.9, mdd!!.calculate(series, tradingRecord))
    }

    @Test
    fun betterThan() {
        val criterion = getCriterion()
        Assert.assertTrue(criterion!!.betterThan(numOf(0.9)!!, numOf(1.5)!!))
        Assert.assertFalse(criterion.betterThan(numOf(1.2)!!, numOf(0.4)!!))
    }
}
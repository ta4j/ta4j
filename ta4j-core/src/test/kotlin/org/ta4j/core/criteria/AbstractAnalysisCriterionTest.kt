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

import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.BaseStrategy
import org.ta4j.core.CriterionFactory
import org.ta4j.core.Strategy
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.criteria.pnl.GrossReturnCriterion
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import org.ta4j.core.rules.BooleanRule
import org.ta4j.core.rules.FixedRule
import java.util.function.Function

class AbstractAnalysisCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { GrossReturnCriterion() }, numFunction
) {
    private var alwaysStrategy: Strategy? = null
    private var buyAndHoldStrategy: Strategy? = null
    private var strategies: MutableList<Strategy> = ArrayList()
    @Before
    fun setUp() {
        alwaysStrategy = BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE)
        buyAndHoldStrategy = BaseStrategy(FixedRule(0), FixedRule(4))
        strategies = ArrayList()
        strategies.add(alwaysStrategy!!)
        strategies.add(buyAndHoldStrategy!!)
    }

    @Test
    fun bestShouldBeAlwaysOperateOnProfit() {
        val series = MockBarSeries(numFunction, 6.0, 9.0, 6.0, 6.0)
        val manager = BarSeriesManager(series)
        val bestStrategy = getCriterion()!!.chooseBest(manager, TradeType.BUY, strategies)
        TestCase.assertEquals(alwaysStrategy, bestStrategy)
    }

    @Test
    fun bestShouldBeBuyAndHoldOnLoss() {
        val series = MockBarSeries(numFunction, 6.0, 3.0, 6.0, 6.0)
        val manager = BarSeriesManager(series)
        val bestStrategy = getCriterion()!!.chooseBest(manager, TradeType.BUY, strategies)
        TestCase.assertEquals(buyAndHoldStrategy, bestStrategy)
    }

    @Test
    fun toStringMethod() {
        val c1: AbstractAnalysisCriterion = AverageReturnPerBarCriterion()
        TestCase.assertEquals("Average Return Per Bar", c1.toString())
        val c2: AbstractAnalysisCriterion = EnterAndHoldReturnCriterion()
        TestCase.assertEquals("Enter And Hold Return", c2.toString())
        val c3: AbstractAnalysisCriterion = ReturnOverMaxDrawdownCriterion()
        TestCase.assertEquals("Return Over Max Drawdown", c3.toString())
    }
}
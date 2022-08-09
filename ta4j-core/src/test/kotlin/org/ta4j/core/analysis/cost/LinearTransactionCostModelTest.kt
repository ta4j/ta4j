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
package org.ta4j.core.analysis.cost

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import org.ta4j.core.rules.FixedRule
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

class LinearTransactionCostModelTest {
    private var transactionModel: CostModel? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        transactionModel = LinearTransactionCostModel(0.01)
    }

    @Test
    fun calculateSingleTradeCost() {
        // Price - Amount calculation Test
        val price: Num = DoubleNum.valueOf(100)
        val amount: Num = DoubleNum.valueOf(2)
        val cost = transactionModel!!.calculate(price, amount)
        TestUtils.assertNumEquals(DoubleNum.valueOf(2), cost)
    }

    @Test
    fun calculateBuyPosition() {
        // Calculate the transaction costs of a closed long position
        val holdingPeriod = 2
        val entry = buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel)
        val exit = sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel)
        val position = Position(entry, exit, transactionModel, ZeroCostModel())
        val costFromBuy = entry.cost
        val costFromSell = exit.cost
        val costsFromModel = transactionModel!!.calculate(position, holdingPeriod)
        TestUtils.assertNumEquals(costsFromModel, costFromBuy!!.plus(costFromSell!!))
        TestUtils.assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1))
        TestUtils.assertNumEquals(costFromBuy, DoubleNum.valueOf(1))
    }

    @Test
    fun calculateSellPosition() {
        // Calculate the transaction costs of a closed short position
        val holdingPeriod = 2
        val entry = sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel)
        val exit = buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel)
        val position = Position(entry, exit, transactionModel, ZeroCostModel())
        val costFromBuy = entry.cost
        val costFromSell = exit.cost
        val costsFromModel = transactionModel!!.calculate(position, holdingPeriod)
        TestUtils.assertNumEquals(costsFromModel, costFromBuy!!.plus(costFromSell!!))
        TestUtils.assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1))
        TestUtils.assertNumEquals(costFromBuy, DoubleNum.valueOf(1))
    }

    @Test
    fun calculateOpenSellPosition() {
        // Calculate the transaction costs of an open position
        val currentIndex = 4
        val position = Position(TradeType.BUY, transactionModel, ZeroCostModel())
        position.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        val costsFromModel = transactionModel!!.calculate(position, currentIndex)
        TestUtils.assertNumEquals(costsFromModel, DoubleNum.valueOf(1))
    }

    @Test
    fun testEquality() {
        val model = LinearTransactionCostModel(0.1)
        val modelSameClass: CostModel = LinearTransactionCostModel(0.2)
        val modelSameFee: CostModel = LinearTransactionCostModel(0.1)
        val modelOther: CostModel = ZeroCostModel()
        val equality = model.equals(modelSameFee)
        val inequality1 = model.equals(modelSameClass)
        val inequality2 = model.equals(modelOther)
        Assert.assertTrue(equality)
        Assert.assertFalse(inequality1)
        Assert.assertFalse(inequality2)
    }

    @Test
    fun testBacktesting() {
        val series = BaseBarSeriesBuilder().withName("CostModel test").build()
        val now = ZonedDateTime.now()
        val one = series.numOf(1)
        val two = series.numOf(2)
        val three = series.numOf(3)
        val four = series.numOf(4)
        series.addBar(now, one, one, one, one, one)
        series.addBar(now.plusSeconds(1), two, two, two, two, two)
        series.addBar(now.plusSeconds(2), three, three, three, three, three)
        series.addBar(now.plusSeconds(3), four, four, four, four, four)
        val entryRule: Rule = FixedRule(0, 2)
        val exitRule: Rule = FixedRule(1, 3)
        val strategies: MutableList<Strategy> = LinkedList()
        strategies.add(BaseStrategy("Cost model test strategy", entryRule, exitRule))
        val orderFee = series.numOf(BigDecimal("0.0026"))
        val executor = BacktestExecutor(
            series, LinearTransactionCostModel(orderFee.doubleValue()),
            ZeroCostModel()
        )
        val amount = series.numOf(25)
        val strategyResult = executor.execute(strategies, amount)[0]!!
        val firstPositionBuy = one.plus(one.times(orderFee))
        val firstPositionSell = two.minus(two.times(orderFee))
        val firstPositionProfit = firstPositionSell.minus(firstPositionBuy).times(amount)
        val secondPositionBuy = three.plus(three.times(orderFee))
        val secondPositionSell = four.minus(four.times(orderFee))
        val secondPositionProfit = secondPositionSell.minus(secondPositionBuy).times(amount)
        val overallProfit = firstPositionProfit.plus(secondPositionProfit)
        Assert.assertEquals(overallProfit, strategyResult.performanceReport!!.totalProfit)
    }
}
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
import org.ta4j.core.Position
import org.ta4j.core.TestUtils
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num

class LinearBorrowingCostModelTest {
    private var borrowingModel: CostModel? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        borrowingModel = LinearBorrowingCostModel(0.01)
    }

    @Test
    fun calculateZeroTest() {
        // Price - Amount calculation Test
        val price: Num = DoubleNum.valueOf(100)
        val amount: Num = DoubleNum.valueOf(2)
        val cost = borrowingModel!!.calculate(price, amount)
        TestUtils.assertNumEquals(DoubleNum.valueOf(0), cost)
    }

    @Test
    fun calculateBuyPosition() {
        // Holding a bought asset should not incur borrowing costs
        val holdingPeriod = 2
        val entry = buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        val exit = sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1))
        val position = Position(entry, exit, ZeroCostModel(), borrowingModel!!)
        val costsFromPosition = position.holdingCost
        val costsFromModel = borrowingModel!!.calculate(position, holdingPeriod)
        TestUtils.assertNumEquals(costsFromModel, costsFromPosition)
        TestUtils.assertNumEquals(costsFromModel, DoubleNum.valueOf(0))
    }

    @Test
    fun calculateSellPosition() {
        // Short selling incurs borrowing costs
        val holdingPeriod = 2
        val entry = sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        val exit = buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1))
        val position = Position(entry, exit, ZeroCostModel(), borrowingModel!!)
        val costsFromPosition = position.holdingCost
        val costsFromModel = borrowingModel!!.calculate(position, holdingPeriod)
        TestUtils.assertNumEquals(costsFromModel, costsFromPosition)
        TestUtils.assertNumEquals(costsFromModel, DoubleNum.valueOf(2))
    }

    @Test
    fun calculateOpenSellPosition() {
        // Short selling incurs borrowing costs. Since position is still open, accounted
        // for until current index
        val currentIndex = 4
        val position = Position(TradeType.SELL, ZeroCostModel(), borrowingModel)
        position.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        val costsFromPosition = position.getHoldingCost(currentIndex)
        val costsFromModel = borrowingModel!!.calculate(position, currentIndex)
        TestUtils.assertNumEquals(costsFromModel, costsFromPosition)
        TestUtils.assertNumEquals(costsFromModel, DoubleNum.valueOf(4))
    }

    @Test
    fun testEquality() {
        val model = LinearBorrowingCostModel(0.1)
        val modelSameClass: CostModel = LinearBorrowingCostModel(0.2)
        val modelSameFee: CostModel = LinearBorrowingCostModel(0.1)
        val modelOther: CostModel = ZeroCostModel()
        val equality = model.equals(modelSameFee)
        val inequality1 = model.equals(modelSameClass)
        val inequality2 = model.equals(modelOther)
        Assert.assertTrue(equality)
        Assert.assertFalse(inequality1)
        Assert.assertFalse(inequality2)
    }
}
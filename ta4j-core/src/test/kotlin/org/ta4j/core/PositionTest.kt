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
package org.ta4j.core

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.analysis.cost.CostModel
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel
import org.ta4j.core.analysis.cost.LinearTransactionCostModel
import org.ta4j.core.analysis.cost.ZeroCostModel
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.util.function.Function

class PositionTest {
    private var newPosition: Position? = null
    private var uncoveredPosition: Position? = null
    private var posEquals1: Position? = null
    private var posEquals2: Position? = null
    private var posNotEquals1: Position? = null
    private var posNotEquals2: Position? = null
    private var transactionModel: CostModel? = null
    private var holdingModel: CostModel? = null
    private var enter: Trade? = null
    private var exitSameType: Trade? = null
    private var exitDifferentType: Trade? = null
    @Before
    fun setUp() {
        newPosition = Position()
        uncoveredPosition = Position(TradeType.SELL)
        posEquals1 = Position()
        posEquals1!!.operate(1)
        posEquals1!!.operate(2)
        posEquals2 = Position()
        posEquals2!!.operate(1)
        posEquals2!!.operate(2)
        posNotEquals1 = Position(TradeType.SELL)
        posNotEquals1!!.operate(1)
        posNotEquals1!!.operate(2)
        posNotEquals2 = Position(TradeType.SELL)
        posNotEquals2!!.operate(1)
        posNotEquals2!!.operate(2)
        transactionModel = LinearTransactionCostModel(0.01)
        holdingModel = LinearBorrowingCostModel(0.001)
        enter = buyAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel)
        exitSameType = sellAt(2, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel)
        exitDifferentType = buyAt(2, DoubleNum.valueOf(2), DoubleNum.valueOf(1))
    }

    @Test
    fun whenNewShouldCreateBuyOrderWhenEntering() {
        newPosition!!.operate(0)
        Assert.assertEquals(buyAt(0, NaN.NaN, NaN.NaN), newPosition!!.entry)
    }

    @Test
    fun whenNewShouldNotExit() {
        Assert.assertFalse(newPosition!!.isOpened)
    }

    @Test
    fun whenOpenedShouldCreateSellOrderWhenExiting() {
        newPosition!!.operate(0)
        newPosition!!.operate(1)
        Assert.assertEquals(sellAt(1, NaN.NaN, NaN.NaN), newPosition!!.exit)
    }

    @Test
    fun whenClosedShouldNotEnter() {
        newPosition!!.operate(0)
        newPosition!!.operate(1)
        Assert.assertTrue(newPosition!!.isClosed)
        newPosition!!.operate(2)
        Assert.assertTrue(newPosition!!.isClosed)
    }

    @Test(expected = IllegalStateException::class)
    fun whenExitIndexIsLessThanEntryIndexShouldThrowException() {
        newPosition!!.operate(3)
        newPosition!!.operate(1)
    }

    @Test
    fun shouldClosePositionOnSameIndex() {
        newPosition!!.operate(3)
        newPosition!!.operate(3)
        Assert.assertTrue(newPosition!!.isClosed)
    }

//    @Test(expected = IllegalArgumentException::class)
//    fun shouldThrowIllegalArgumentExceptionWhenOrderTypeIsNull() {
//        Position(null)
//    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldThrowIllegalArgumentExceptionWhenOrdersHaveSameType() {
        Position(buyAt(0, NaN.NaN, NaN.NaN), buyAt(1, NaN.NaN, NaN.NaN))
    }

    @Test
    fun whenNewShouldCreateSellOrderWhenEnteringUncovered() {
        uncoveredPosition!!.operate(0)
        Assert.assertEquals(sellAt(0, NaN.NaN, NaN.NaN), uncoveredPosition!!.entry)
    }

    @Test
    fun whenOpenedShouldCreateBuyOrderWhenExitingUncovered() {
        uncoveredPosition!!.operate(0)
        uncoveredPosition!!.operate(1)
        Assert.assertEquals(buyAt(1, NaN.NaN, NaN.NaN), uncoveredPosition!!.exit)
    }

    @Test
    fun overrideToString() {
        Assert.assertEquals(posEquals1.toString(), posEquals2.toString())
        Assert.assertNotEquals(posEquals1.toString(), posNotEquals1.toString())
        Assert.assertNotEquals(posEquals1.toString(), posNotEquals2.toString())
    }

    @Test
    fun testEqualsForNewPositions() {
        Assert.assertEquals(newPosition, Position())
        Assert.assertNotEquals(newPosition, Any())
        Assert.assertNotEquals(newPosition, null)
    }

    @Test
    fun testEqualsForEntryOrders() {
        val trLeft = newPosition
        val trRightEquals = Position()
        val trRightNotEquals = Position()
        Assert.assertEquals(TradeType.BUY, trRightNotEquals.operate(2)!!.type)
        Assert.assertNotEquals(trLeft, trRightNotEquals)
        Assert.assertEquals(TradeType.BUY, trLeft!!.operate(1)!!.type)
        Assert.assertEquals(TradeType.BUY, trRightEquals.operate(1)!!.type)
        Assert.assertEquals(trLeft, trRightEquals)
        Assert.assertNotEquals(trLeft, trRightNotEquals)
    }

    @Test
    fun testEqualsForExitOrders() {
        val trLeft = newPosition
        val trRightEquals = Position()
        val trRightNotEquals = Position()
        Assert.assertEquals(TradeType.BUY, trLeft!!.operate(1)!!.type)
        Assert.assertEquals(TradeType.BUY, trRightEquals.operate(1)!!.type)
        Assert.assertEquals(TradeType.BUY, trRightNotEquals.operate(1)!!.type)
        Assert.assertEquals(TradeType.SELL, trRightNotEquals.operate(3)!!.type)
        Assert.assertNotEquals(trLeft, trRightNotEquals)
        Assert.assertEquals(TradeType.SELL, trLeft.operate(2)!!.type)
        Assert.assertEquals(TradeType.SELL, trRightEquals.operate(2)!!.type)
        Assert.assertEquals(trLeft, trRightEquals)
        Assert.assertNotEquals(trLeft, trRightNotEquals)
    }

    @Test
    fun testGetProfitForLongPositions() {
        val position = Position(TradeType.BUY)
        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2))
        position.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2))
        val profit = position.profit
        Assert.assertEquals(DoubleNum.valueOf(4.0), profit)
    }

    @Test
    fun testGetProfitForShortPositions() {
        val position = Position(TradeType.SELL)
        position.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2))
        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2))
        val profit = position.profit
        Assert.assertEquals(DoubleNum.valueOf(4.0), profit)
    }

    @Test
    fun testGetGrossReturnForLongPositions() {
        val position = Position(TradeType.BUY)
        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2))
        position.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2))
        val profit = position.grossReturn
        Assert.assertEquals(DoubleNum.valueOf(1.2), profit)
    }

    @Test
    fun testGetGrossReturnForShortPositions() {
        val position = Position(TradeType.SELL)
        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2))
        position.operate(0, DoubleNum.valueOf(8.00), DoubleNum.valueOf(2))
        val profit = position.grossReturn
        Assert.assertEquals(DoubleNum.valueOf(1.2), profit)
    }

    @Test
    fun testGetGrossReturnForLongPositionsUsingBarCloseOnNaN() {
        val series =
            MockBarSeries(Function<Number?, Num> { obj: Number? -> DoubleNum.Companion.valueOf(obj) }, 100.0, 105.0)
        val position = Position(Trade(0, TradeType.BUY, NaN.NaN, NaN.NaN), Trade(1, TradeType.SELL, NaN.NaN, NaN.NaN))
        TestUtils.assertNumEquals(DoubleNum.valueOf(1.05), position.getGrossReturn(series))
    }

    @Test
    fun testGetGrossReturnForShortPositionsUsingBarCloseOnNaN() {
        val series =
            MockBarSeries(Function<Number?, Num> { obj: Number? -> DoubleNum.Companion.valueOf(obj) }, 100.0, 95.0)
        val position = Position(Trade(0, TradeType.SELL, NaN.NaN, NaN.NaN), Trade(1, TradeType.BUY, NaN.NaN, NaN.NaN))
        TestUtils.assertNumEquals(DoubleNum.valueOf(1.05), position.getGrossReturn(series))
    }

    @Test
    fun testCostModelConsistencyTrue() {
        Position(enter!!, exitSameType!!, transactionModel, holdingModel!!)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCostModelEntryInconsistent() {
        Position(enter!!, exitDifferentType!!, ZeroCostModel(), holdingModel!!)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCostModelExitInconsistent() {
        Position(enter!!, exitDifferentType!!, transactionModel, holdingModel!!)
    }

    @Test
    fun getProfitLongNoFinalBarTest() {
        val closedPosition = Position(enter!!, exitSameType!!, transactionModel, holdingModel!!)
        val openPosition = Position(TradeType.BUY, transactionModel, holdingModel)
        openPosition.operate(5, DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        val profitOfClosedPosition = closedPosition.profit
        val proftOfOpenPosition = openPosition.profit
        TestUtils.assertNumEquals(DoubleNum.valueOf(-0.04), profitOfClosedPosition)
        TestUtils.assertNumEquals(DoubleNum.valueOf(0), proftOfOpenPosition)
    }

    @Test
    fun getProfitLongWithFinalBarTest() {
        val closedPosition = Position(enter!!, exitSameType!!, transactionModel, holdingModel!!)
        val openPosition = Position(TradeType.BUY, transactionModel, holdingModel)
        openPosition.operate(5, DoubleNum.valueOf(2), DoubleNum.valueOf(1))
        val profitOfClosedPosition = closedPosition.getProfit(10, DoubleNum.valueOf(12))
        val profitOfOpenPosition = openPosition.getProfit(10, DoubleNum.valueOf(12))
        TestUtils.assertNumEquals(DoubleNum.valueOf(9.98), profitOfOpenPosition)
        TestUtils.assertNumEquals(DoubleNum.valueOf(-0.04), profitOfClosedPosition)
    }

    @Test
    fun getProfitShortNoFinalBarTest() {
        val sell = sellAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel)
        val buyBack = buyAt(10, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel)
        val closedPosition = Position(sell, buyBack, transactionModel, holdingModel!!)
        val openPosition = Position(TradeType.SELL, transactionModel, holdingModel)
        openPosition.operate(5, DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        val profitOfClosedPosition = closedPosition.profit
        val proftOfOpenPosition = openPosition.profit
        val expectedHoldingCosts: Num = DoubleNum.valueOf(2.0 * 9.0 * 0.001)
        val expectedProfitOfClosedPosition = DoubleNum.valueOf(-0.04).minus(expectedHoldingCosts)
        TestUtils.assertNumEquals(expectedProfitOfClosedPosition, profitOfClosedPosition)
        TestUtils.assertNumEquals(DoubleNum.valueOf(0), proftOfOpenPosition)
    }

    @Test
    fun getProfitShortWithFinalBarTest() {
        val sell = sellAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel)
        val buyBack = buyAt(10, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel)
        val closedPosition = Position(sell, buyBack, transactionModel, holdingModel!!)
        val openPosition = Position(TradeType.SELL, transactionModel, holdingModel)
        openPosition.operate(5, DoubleNum.valueOf(2), DoubleNum.valueOf(1))
        val profitOfClosedPositionFinalAfter = closedPosition.getProfit(20, DoubleNum.valueOf(3))
        val profitOfOpenPositionFinalAfter = openPosition.getProfit(20, DoubleNum.valueOf(3))
        val profitOfClosedPositionFinalBefore = closedPosition.getProfit(5, DoubleNum.valueOf(3))
        val profitOfOpenPositionFinalBefore = openPosition.getProfit(5, DoubleNum.valueOf(3))
        val expectedHoldingCosts: Num = DoubleNum.valueOf(2.0 * 9.0 * 0.001)
        val expectedProfitOfClosedPosition = DoubleNum.valueOf(-0.04).minus(expectedHoldingCosts)
        TestUtils.assertNumEquals(DoubleNum.valueOf(-1.05), profitOfOpenPositionFinalAfter)
        TestUtils.assertNumEquals(DoubleNum.valueOf(-1.02), profitOfOpenPositionFinalBefore)
        TestUtils.assertNumEquals(expectedProfitOfClosedPosition, profitOfClosedPositionFinalAfter)
        TestUtils.assertNumEquals(expectedProfitOfClosedPosition, profitOfClosedPositionFinalBefore)
    }
}
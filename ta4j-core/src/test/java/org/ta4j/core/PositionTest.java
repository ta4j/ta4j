/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class PositionTest {

    private Position newPosition, uncoveredPosition, posEquals1, posEquals2, posNotEquals1, posNotEquals2;

    private CostModel transactionModel;
    private CostModel holdingModel;
    private Trade enter;
    private Trade exitSameType;
    private Trade exitDifferentType;

    @Before
    public void setUp() {
        this.newPosition = new Position();
        this.uncoveredPosition = new Position(TradeType.SELL);

        posEquals1 = new Position();
        posEquals1.operate(1);
        posEquals1.operate(2);

        posEquals2 = new Position();
        posEquals2.operate(1);
        posEquals2.operate(2);

        posNotEquals1 = new Position(TradeType.SELL);
        posNotEquals1.operate(1);
        posNotEquals1.operate(2);

        posNotEquals2 = new Position(TradeType.SELL);
        posNotEquals2.operate(1);
        posNotEquals2.operate(2);

        transactionModel = new LinearTransactionCostModel(0.01);
        holdingModel = new LinearBorrowingCostModel(0.001);

        enter = Trade.buyAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        exitSameType = Trade.sellAt(2, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        exitDifferentType = Trade.buyAt(2, DoubleNum.valueOf(2), DoubleNum.valueOf(1));
    }

    @Test
    public void whenNewShouldCreateBuyOrderWhenEntering() {
        newPosition.operate(0);
        assertEquals(Trade.buyAt(0, NaN, NaN), newPosition.getEntry());
    }

    @Test
    public void whenNewShouldNotExit() {
        assertFalse(newPosition.isOpened());
    }

    @Test
    public void whenOpenedShouldCreateSellOrderWhenExiting() {
        newPosition.operate(0);
        newPosition.operate(1);
        assertEquals(Trade.sellAt(1, NaN, NaN), newPosition.getExit());
    }

    @Test
    public void whenClosedShouldNotEnter() {
        newPosition.operate(0);
        newPosition.operate(1);
        assertTrue(newPosition.isClosed());
        newPosition.operate(2);
        assertTrue(newPosition.isClosed());
    }

    @Test(expected = IllegalStateException.class)
    public void whenExitIndexIsLessThanEntryIndexShouldThrowException() {
        newPosition.operate(3);
        newPosition.operate(1);
    }

    @Test
    public void shouldClosePositionOnSameIndex() {
        newPosition.operate(3);
        newPosition.operate(3);
        assertTrue(newPosition.isClosed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrderTypeIsNull() {
        new Position(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrdersHaveSameType() {
        new Position(Trade.buyAt(0, NaN, NaN), Trade.buyAt(1, NaN, NaN));
    }

    @Test
    public void whenNewShouldCreateSellOrderWhenEnteringUncovered() {
        uncoveredPosition.operate(0);
        assertEquals(Trade.sellAt(0, NaN, NaN), uncoveredPosition.getEntry());
    }

    @Test
    public void whenOpenedShouldCreateBuyOrderWhenExitingUncovered() {
        uncoveredPosition.operate(0);
        uncoveredPosition.operate(1);
        assertEquals(Trade.buyAt(1, NaN, NaN), uncoveredPosition.getExit());
    }

    @Test
    public void overrideToString() {
        assertEquals(posEquals1.toString(), posEquals2.toString());
        assertNotEquals(posEquals1.toString(), posNotEquals1.toString());
        assertNotEquals(posEquals1.toString(), posNotEquals2.toString());
    }

    @Test
    public void testEqualsForNewPositions() {
        assertEquals(newPosition, new Position());
        assertNotEquals(newPosition, new Object());
        assertNotEquals(newPosition, null);
    }

    @Test
    public void testEqualsForEntryOrders() {
        Position trLeft = newPosition;
        Position trRightEquals = new Position();
        Position trRightNotEquals = new Position();

        assertEquals(TradeType.BUY, trRightNotEquals.operate(2).getType());
        assertNotEquals(trLeft, trRightNotEquals);

        assertEquals(TradeType.BUY, trLeft.operate(1).getType());
        assertEquals(TradeType.BUY, trRightEquals.operate(1).getType());
        assertEquals(trLeft, trRightEquals);

        assertNotEquals(trLeft, trRightNotEquals);
    }

    @Test
    public void testEqualsForExitOrders() {
        Position trLeft = newPosition;
        Position trRightEquals = new Position();
        Position trRightNotEquals = new Position();

        assertEquals(TradeType.BUY, trLeft.operate(1).getType());
        assertEquals(TradeType.BUY, trRightEquals.operate(1).getType());
        assertEquals(TradeType.BUY, trRightNotEquals.operate(1).getType());

        assertEquals(TradeType.SELL, trRightNotEquals.operate(3).getType());
        assertNotEquals(trLeft, trRightNotEquals);

        assertEquals(TradeType.SELL, trLeft.operate(2).getType());
        assertEquals(TradeType.SELL, trRightEquals.operate(2).getType());
        assertEquals(trLeft, trRightEquals);

        assertNotEquals(trLeft, trRightNotEquals);
    }

    @Test
    public void testGetProfitForLongPositions() {
        Position position = new Position(TradeType.BUY);

        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));
        position.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2));

        final Num profit = position.getProfit();

        assertEquals(DoubleNum.valueOf(4.0), profit);
    }

    @Test
    public void testGetProfitForShortPositions() {
        Position position = new Position(TradeType.SELL);

        position.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2));
        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));

        final Num profit = position.getProfit();

        assertEquals(DoubleNum.valueOf(4.0), profit);
    }

    @Test
    public void testGetGrossReturnForLongPositions() {
        Position position = new Position(TradeType.BUY);

        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));
        position.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2));

        final Num profit = position.getGrossReturn();

        assertEquals(DoubleNum.valueOf(1.2), profit);
    }

    @Test
    public void testGetGrossReturnForShortPositions() {
        Position position = new Position(TradeType.SELL);

        position.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));
        position.operate(0, DoubleNum.valueOf(8.00), DoubleNum.valueOf(2));

        final Num profit = position.getGrossReturn();

        assertEquals(DoubleNum.valueOf(1.2), profit);
    }

    @Test
    public void testGetGrossReturnForLongPositionsUsingBarCloseOnNaN() {
        MockBarSeries series = new MockBarSeries(DoubleNum::valueOf, 100, 105);
        Position position = new Position(new Trade(0, TradeType.BUY, NaN, NaN), new Trade(1, TradeType.SELL, NaN, NaN));
        assertNumEquals(DoubleNum.valueOf(1.05), position.getGrossReturn(series));
    }

    @Test
    public void testGetGrossReturnForShortPositionsUsingBarCloseOnNaN() {
        MockBarSeries series = new MockBarSeries(DoubleNum::valueOf, 100, 95);
        Position position = new Position(new Trade(0, TradeType.SELL, NaN, NaN), new Trade(1, TradeType.BUY, NaN, NaN));
        assertNumEquals(DoubleNum.valueOf(1.05), position.getGrossReturn(series));
    }

    @Test
    public void testCostModelConsistencyTrue() {
        new Position(enter, exitSameType, transactionModel, holdingModel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCostModelEntryInconsistent() {
        new Position(enter, exitDifferentType, new ZeroCostModel(), holdingModel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCostModelExitInconsistent() {
        new Position(enter, exitDifferentType, transactionModel, holdingModel);
    }

    @Test
    public void getProfitLongNoFinalBarTest() {
        Position closedPosition = new Position(enter, exitSameType, transactionModel, holdingModel);
        Position openPosition = new Position(TradeType.BUY, transactionModel, holdingModel);
        openPosition.operate(5, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num profitOfClosedPosition = closedPosition.getProfit();
        Num proftOfOpenPosition = openPosition.getProfit();

        assertNumEquals(DoubleNum.valueOf(-0.04), profitOfClosedPosition);
        assertNumEquals(DoubleNum.valueOf(0), proftOfOpenPosition);
    }

    @Test
    public void getProfitLongWithFinalBarTest() {
        Position closedPosition = new Position(enter, exitSameType, transactionModel, holdingModel);
        Position openPosition = new Position(TradeType.BUY, transactionModel, holdingModel);
        openPosition.operate(5, DoubleNum.valueOf(2), DoubleNum.valueOf(1));

        Num profitOfClosedPosition = closedPosition.getProfit(10, DoubleNum.valueOf(12));
        Num profitOfOpenPosition = openPosition.getProfit(10, DoubleNum.valueOf(12));

        assertNumEquals(DoubleNum.valueOf(9.98), profitOfOpenPosition);
        assertNumEquals(DoubleNum.valueOf(-0.04), profitOfClosedPosition);
    }

    @Test
    public void getProfitShortNoFinalBarTest() {
        Trade sell = Trade.sellAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        Trade buyBack = Trade.buyAt(10, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);

        Position closedPosition = new Position(sell, buyBack, transactionModel, holdingModel);
        Position openPosition = new Position(TradeType.SELL, transactionModel, holdingModel);
        openPosition.operate(5, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num profitOfClosedPosition = closedPosition.getProfit();
        Num proftOfOpenPosition = openPosition.getProfit();

        Num expectedHoldingCosts = DoubleNum.valueOf(2.0 * 9.0 * 0.001);
        Num expectedProfitOfClosedPosition = DoubleNum.valueOf(-0.04).minus(expectedHoldingCosts);

        assertNumEquals(expectedProfitOfClosedPosition, profitOfClosedPosition);
        assertNumEquals(DoubleNum.valueOf(0), proftOfOpenPosition);
    }

    @Test
    public void getProfitShortWithFinalBarTest() {
        Trade sell = Trade.sellAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        Trade buyBack = Trade.buyAt(10, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);

        Position closedPosition = new Position(sell, buyBack, transactionModel, holdingModel);
        Position openPosition = new Position(TradeType.SELL, transactionModel, holdingModel);
        openPosition.operate(5, DoubleNum.valueOf(2), DoubleNum.valueOf(1));

        Num profitOfClosedPositionFinalAfter = closedPosition.getProfit(20, DoubleNum.valueOf(3));
        Num profitOfOpenPositionFinalAfter = openPosition.getProfit(20, DoubleNum.valueOf(3));
        Num profitOfClosedPositionFinalBefore = closedPosition.getProfit(5, DoubleNum.valueOf(3));
        Num profitOfOpenPositionFinalBefore = openPosition.getProfit(5, DoubleNum.valueOf(3));

        Num expectedHoldingCosts = DoubleNum.valueOf(2.0 * 9.0 * 0.001);
        Num expectedProfitOfClosedPosition = DoubleNum.valueOf(-0.04).minus(expectedHoldingCosts);

        assertNumEquals(DoubleNum.valueOf(-1.05), profitOfOpenPositionFinalAfter);
        assertNumEquals(DoubleNum.valueOf(-1.02), profitOfOpenPositionFinalBefore);
        assertNumEquals(expectedProfitOfClosedPosition, profitOfClosedPositionFinalAfter);
        assertNumEquals(expectedProfitOfClosedPosition, profitOfClosedPositionFinalBefore);
    }
}

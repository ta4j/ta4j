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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.LinearBorrowingCostModel;
import org.ta4j.core.cost.LinearTransactionCostModel;
import org.ta4j.core.cost.ZeroCostModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.num.NaN.NaN;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class PosPairTest {

    private PosPair newPosPair, uncoveredPosPair, pEquals1, pEquals2, pNotEquals1, pNotEquals2;

    private CostModel transactionModel;
    private CostModel holdingModel;
    private Order enter;
    private Order exitSameType;
    private Order exitDifferentType;

    @Before
    public void setUp() {
        this.newPosPair = new PosPair();
        this.uncoveredPosPair = new PosPair(OrderType.SELL);

        pEquals1 = new PosPair();
        pEquals1.operate(1);
        pEquals1.operate(2);

        pEquals2 = new PosPair();
        pEquals2.operate(1);
        pEquals2.operate(2);

        pNotEquals1 = new PosPair(OrderType.SELL);
        pNotEquals1.operate(1);
        pNotEquals1.operate(2);

        pNotEquals2 = new PosPair(OrderType.SELL);
        pNotEquals2.operate(1);
        pNotEquals2.operate(2);

        transactionModel = new LinearTransactionCostModel(0.01);
        holdingModel = new LinearBorrowingCostModel(0.001);

        enter = Order.buyAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        exitSameType = Order.sellAt(2, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        exitDifferentType = Order.buyAt(2, DoubleNum.valueOf(2), DoubleNum.valueOf(1));
    }

    @Test
    public void whenNewShouldCreateBuyOrderWhenEntering() {
        newPosPair.operate(0);
        assertEquals(Order.buyAt(0, NaN, NaN), newPosPair.getEntry());
    }

    @Test
    public void whenNewShouldNotExit() {
        assertFalse(newPosPair.isOpened());
    }

    @Test
    public void whenOpenedShouldCreateSellOrderWhenExiting() {
        newPosPair.operate(0);
        newPosPair.operate(1);
        assertEquals(Order.sellAt(1, NaN, NaN), newPosPair.getExit());
    }

    @Test
    public void whenClosedShouldNotEnter() {
        newPosPair.operate(0);
        newPosPair.operate(1);
        assertTrue(newPosPair.isClosed());
        newPosPair.operate(2);
        assertTrue(newPosPair.isClosed());
    }

    @Test(expected = IllegalStateException.class)
    public void whenExitIndexIsLessThanEntryIndexShouldThrowException() {
        newPosPair.operate(3);
        newPosPair.operate(1);
    }

    @Test
    public void shouldCloseTradeOnSameIndex() {
        newPosPair.operate(3);
        newPosPair.operate(3);
        assertTrue(newPosPair.isClosed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrderTypeIsNull() {
        new PosPair(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenOrdersHaveSameType() {
        new PosPair(Order.buyAt(0, NaN, NaN), Order.buyAt(1, NaN, NaN));
    }

    @Test
    public void whenNewShouldCreateSellOrderWhenEnteringUncovered() {
        uncoveredPosPair.operate(0);
        assertEquals(Order.sellAt(0, NaN, NaN), uncoveredPosPair.getEntry());
    }

    @Test
    public void whenOpenedShouldCreateBuyOrderWhenExitingUncovered() {
        uncoveredPosPair.operate(0);
        uncoveredPosPair.operate(1);
        assertEquals(Order.buyAt(1, NaN, NaN), uncoveredPosPair.getExit());
    }

    @Test
    public void overrideToString() {
        assertEquals(pEquals1.toString(), pEquals2.toString());
        assertNotEquals(pEquals1.toString(), pNotEquals1.toString());
        assertNotEquals(pEquals1.toString(), pNotEquals2.toString());
    }

    @Test
    public void testEqualsForNewTrades() {
        assertEquals(newPosPair, new PosPair());
        assertNotEquals(newPosPair, new Object());
        assertNotEquals(newPosPair, null);
    }

    @Test
    public void testEqualsForEntryOrders() {
        PosPair trLeft = newPosPair;
        PosPair trRightEquals = new PosPair();
        PosPair trRightNotEquals = new PosPair();

        assertEquals(OrderType.BUY, trRightNotEquals.operate(2).getType());
        assertNotEquals(trLeft, trRightNotEquals);

        assertEquals(OrderType.BUY, trLeft.operate(1).getType());
        assertEquals(OrderType.BUY, trRightEquals.operate(1).getType());
        assertEquals(trLeft, trRightEquals);

        assertNotEquals(trLeft, trRightNotEquals);
    }

    @Test
    public void testEqualsForExitOrders() {
        PosPair trLeft = newPosPair;
        PosPair trRightEquals = new PosPair();
        PosPair trRightNotEquals = new PosPair();

        assertEquals(OrderType.BUY, trLeft.operate(1).getType());
        assertEquals(OrderType.BUY, trRightEquals.operate(1).getType());
        assertEquals(OrderType.BUY, trRightNotEquals.operate(1).getType());

        assertEquals(OrderType.SELL, trRightNotEquals.operate(3).getType());
        assertNotEquals(trLeft, trRightNotEquals);

        assertEquals(OrderType.SELL, trLeft.operate(2).getType());
        assertEquals(OrderType.SELL, trRightEquals.operate(2).getType());
        assertEquals(trLeft, trRightEquals);

        assertNotEquals(trLeft, trRightNotEquals);
    }

    @Test
    public void testGetProfitForLongTrades() {
        PosPair trade = new PosPair(OrderType.BUY);

        trade.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));
        trade.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2));

        final Num profit = trade.getProfit();

        assertEquals(DoubleNum.valueOf(4.0), profit);
    }

    @Test
    public void testGetProfitForShortTrades() {
        PosPair trade = new PosPair(OrderType.SELL);

        trade.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2));
        trade.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));

        final Num profit = trade.getProfit();

        assertEquals(DoubleNum.valueOf(4.0), profit);
    }

    @Test
    public void testGetGrossReturnForLongTrades() {
        PosPair trade = new PosPair(OrderType.BUY);

        trade.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));
        trade.operate(0, DoubleNum.valueOf(12.00), DoubleNum.valueOf(2));

        final Num profit = trade.getGrossReturn();

        assertEquals(DoubleNum.valueOf(1.2), profit);
    }

    @Test
    public void testGetGrossReturnForShortTrades() {
        PosPair trade = new PosPair(OrderType.SELL);

        trade.operate(0, DoubleNum.valueOf(10.00), DoubleNum.valueOf(2));
        trade.operate(0, DoubleNum.valueOf(8.00), DoubleNum.valueOf(2));

        final Num profit = trade.getGrossReturn();

        assertEquals(DoubleNum.valueOf(1.2), profit);
    }

    @Test
    public void testGetGrossReturnForLongTradesUsingBarCloseOnNaN() {
        MockBarSeries series = new MockBarSeries(DoubleNum::valueOf, 100, 105);
        PosPair trade = new PosPair(new Order(0, OrderType.BUY, NaN, NaN), new Order(1, OrderType.SELL, NaN, NaN));
        assertNumEquals(DoubleNum.valueOf(1.05), trade.getGrossReturn(series));
    }

    @Test
    public void testGetGrossReturnForShortTradesUsingBarCloseOnNaN() {
        MockBarSeries series = new MockBarSeries(DoubleNum::valueOf, 100, 95);
        PosPair trade = new PosPair(new Order(0, OrderType.SELL, NaN, NaN), new Order(1, OrderType.BUY, NaN, NaN));
        assertNumEquals(DoubleNum.valueOf(1.05), trade.getGrossReturn(series));
    }

    @Test
    public void testCostModelConsistencyTrue() {
        new PosPair(enter, exitSameType, transactionModel, holdingModel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCostModelEntryInconsistent() {
        new PosPair(enter, exitDifferentType, new ZeroCostModel(), holdingModel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCostModelExitInconsistent() {
        new PosPair(enter, exitDifferentType, transactionModel, holdingModel);
    }

    @Test
    public void getProfitLongNoFinalBarTest() {
        PosPair closedTrade = new PosPair(enter, exitSameType, transactionModel, holdingModel);
        PosPair openTrade = new PosPair(OrderType.BUY, transactionModel, holdingModel);
        openTrade.operate(5, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num profitOfClosedTrade = closedTrade.getProfit();
        Num proftOfOpenTrade = openTrade.getProfit();

        assertNumEquals(DoubleNum.valueOf(-0.04), profitOfClosedTrade);
        assertNumEquals(DoubleNum.valueOf(0), proftOfOpenTrade);
    }

    @Test
    public void getProfitLongWithFinalBarTest() {
        PosPair closedTrade = new PosPair(enter, exitSameType, transactionModel, holdingModel);
        PosPair openTrade = new PosPair(OrderType.BUY, transactionModel, holdingModel);
        openTrade.operate(5, DoubleNum.valueOf(2), DoubleNum.valueOf(1));

        Num profitOfClosedTrade = closedTrade.getProfit(10, DoubleNum.valueOf(12));
        Num profitOfOpenTrade = openTrade.getProfit(10, DoubleNum.valueOf(12));

        assertNumEquals(DoubleNum.valueOf(9.98), profitOfOpenTrade);
        assertNumEquals(DoubleNum.valueOf(-0.04), profitOfClosedTrade);
    }

    @Test
    public void getProfitShortNoFinalBarTest() {
        Order sell = Order.sellAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        Order buyBack = Order.buyAt(10, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);

        PosPair closedTrade = new PosPair(sell, buyBack, transactionModel, holdingModel);
        PosPair openTrade = new PosPair(OrderType.SELL, transactionModel, holdingModel);
        openTrade.operate(5, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num profitOfClosedTrade = closedTrade.getProfit();
        Num proftOfOpenTrade = openTrade.getProfit();

        Num expectedHoldingCosts = DoubleNum.valueOf(2.0 * 9.0 * 0.001);
        Num expectedProfitOfClosedTrade = DoubleNum.valueOf(-0.04).minus(expectedHoldingCosts);

        assertNumEquals(expectedProfitOfClosedTrade, profitOfClosedTrade);
        assertNumEquals(DoubleNum.valueOf(0), proftOfOpenTrade);
    }

    @Test
    public void getProfitShortWithFinalBarTest() {
        Order sell = Order.sellAt(1, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);
        Order buyBack = Order.buyAt(10, DoubleNum.valueOf(2), DoubleNum.valueOf(1), transactionModel);

        PosPair closedTrade = new PosPair(sell, buyBack, transactionModel, holdingModel);
        PosPair openTrade = new PosPair(OrderType.SELL, transactionModel, holdingModel);
        openTrade.operate(5, DoubleNum.valueOf(2), DoubleNum.valueOf(1));

        Num profitOfClosedTradeFinalAfter = closedTrade.getProfit(20, DoubleNum.valueOf(3));
        Num profitOfOpenTradeFinalAfter = openTrade.getProfit(20, DoubleNum.valueOf(3));
        Num profitOfClosedTradeFinalBefore = closedTrade.getProfit(5, DoubleNum.valueOf(3));
        Num profitOfOpenTradeFinalBefore = openTrade.getProfit(5, DoubleNum.valueOf(3));

        Num expectedHoldingCosts = DoubleNum.valueOf(2.0 * 9.0 * 0.001);
        Num expectedProfitOfClosedTrade = DoubleNum.valueOf(-0.04).minus(expectedHoldingCosts);

        assertNumEquals(DoubleNum.valueOf(-1.05), profitOfOpenTradeFinalAfter);
        assertNumEquals(DoubleNum.valueOf(-1.02), profitOfOpenTradeFinalBefore);
        assertNumEquals(expectedProfitOfClosedTrade, profitOfClosedTradeFinalAfter);
        assertNumEquals(expectedProfitOfClosedTrade, profitOfClosedTradeFinalBefore);
    }
}

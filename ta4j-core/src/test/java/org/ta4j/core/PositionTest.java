/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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

    @Test
    public void whenExitIndexIsLessThanEntryIndexShouldThrowException() {
        newPosition.operate(3);
        assertThrows(IllegalStateException.class, () -> newPosition.operate(1));
    }

    @Test
    public void shouldClosePositionOnSameIndex() {
        newPosition.operate(3);
        newPosition.operate(3);
        assertTrue(newPosition.isClosed());
    }

    @Test
    public void operateWithPrebuiltTradesSupportsEntryAndExit() {
        Position position = new Position(TradeType.BUY, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        Trade entry = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(1, DoubleNum.valueOf(100), DoubleNum.valueOf(1)),
                        new TradeFill(2, DoubleNum.valueOf(101), DoubleNum.valueOf(1))),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(TradeType.SELL,
                List.of(new TradeFill(3, DoubleNum.valueOf(110), DoubleNum.valueOf(2))),
                RecordedTradeCostModel.INSTANCE);

        position.operate(entry);
        position.operate(exit);

        assertEquals(entry, position.getEntry());
        assertEquals(exit, position.getExit());
        assertTrue(position.isClosed());
    }

    @Test
    public void operateWithPrebuiltTradeRejectsMismatchedEntryType() {
        Position position = new Position(TradeType.BUY);
        Trade entry = Trade.sellAt(1, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        assertThrows(IllegalArgumentException.class, () -> position.operate(entry));
    }

    @Test
    public void operateWithPrebuiltTradeRejectsMismatchedExitType() {
        Position position = new Position(TradeType.BUY);
        position.operate(1, DoubleNum.valueOf(100), DoubleNum.valueOf(1));
        Trade exit = Trade.buyAt(2, DoubleNum.valueOf(110), DoubleNum.valueOf(1));

        assertThrows(IllegalArgumentException.class, () -> position.operate(exit));
    }

    @Test
    public void operateWithPrebuiltTradeRejectsExitBeforeEntryIndex() {
        Position position = new Position(TradeType.BUY);
        position.operate(3, DoubleNum.valueOf(100), DoubleNum.valueOf(1));
        Trade exit = Trade.sellAt(2, DoubleNum.valueOf(110), DoubleNum.valueOf(1));

        assertThrows(IllegalStateException.class, () -> position.operate(exit));
    }

    @Test
    public void operateWithPrebuiltTradeRejectsMismatchedCostModel() {
        Position position = new Position(TradeType.BUY, transactionModel, holdingModel);
        Trade entry = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(1, DoubleNum.valueOf(100), DoubleNum.valueOf(1))), new ZeroCostModel());

        assertThrows(IllegalArgumentException.class, () -> position.operate(entry));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenOrderTypeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Position((TradeType) null));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenOrdersHaveSameType() {
        assertThrows(IllegalArgumentException.class,
                () -> new Position(Trade.buyAt(0, NaN, NaN), Trade.buyAt(1, NaN, NaN)));
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
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 105)
                .build();
        Position position = new Position(new BaseTrade(0, TradeType.BUY, NaN, NaN),
                new BaseTrade(1, TradeType.SELL, NaN, NaN));
        assertNumEquals(DoubleNum.valueOf(1.05), position.getGrossReturn(series));
    }

    @Test
    public void testGetGrossReturnForShortPositionsUsingBarCloseOnNaN() {
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95)
                .build();
        Position position = new Position(new BaseTrade(0, TradeType.SELL, NaN, NaN),
                new BaseTrade(1, TradeType.BUY, NaN, NaN));
        assertNumEquals(DoubleNum.valueOf(1.05), position.getGrossReturn(series));
    }

    @Test
    public void testCostModelConsistencyTrue() {
        new Position(enter, exitSameType, transactionModel, holdingModel);
    }

    @Test
    public void exposesTransactionCostModel() {
        Position position = new Position(TradeType.BUY, transactionModel, holdingModel);

        assertSame(transactionModel, position.getTransactionCostModel());
    }

    @Test
    public void exposesHoldingCostModel() {
        Position position = new Position(TradeType.BUY, transactionModel, holdingModel);

        assertSame(holdingModel, position.getHoldingCostModel());
    }

    @Test
    public void openViewAccessorsExposeEntryDerivedValues() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        Trade entry = new BaseTrade(5, Instant.EPOCH, numFactory.numOf(123), numFactory.numOf(2), numFactory.numOf(0.3),
                ExecutionSide.BUY, "order-5", "corr-5");
        Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        assertEquals(ExecutionSide.BUY, position.side());
        assertNumEquals(numFactory.numOf(2), position.amount());
        assertNumEquals(numFactory.numOf(123), position.averageEntryPrice());
        assertNumEquals(numFactory.numOf(246), position.totalEntryCost());
        assertNumEquals(numFactory.numOf(0.3), position.totalFees());
    }

    @Test
    public void openViewAccessorsReturnNullWhenPositionHasNoEntry() {
        Position position = new Position(TradeType.BUY);

        assertNull(position.side());
        assertNull(position.amount());
        assertNull(position.averageEntryPrice());
        assertNull(position.totalEntryCost());
        assertNull(position.totalFees());
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
    public void realizedSpotProfitExcludesUnexecutedExitCost() {
        CostModel fixed = new FixedTransactionCostModel(3);
        Trade futureEntry = Trade.buyAt(2, DoubleNum.valueOf(100), DoubleNum.valueOf(1), fixed);
        Trade futureExit = Trade.sellAt(4, DoubleNum.valueOf(110), DoubleNum.valueOf(1), fixed);
        Position position = new Position(futureEntry, futureExit, fixed, new ZeroCostModel());

        assertNumEquals(0, position.getRealizedProfit(1));
        assertNumEquals(-3, position.getRealizedProfit(2));
        assertNumEquals(-3, position.getRealizedProfit(3));
        assertNumEquals(4, position.getRealizedProfit(4));
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

        Num expectedHoldingCostsAfter = DoubleNum.valueOf(2.0 * 9.0 * 0.001);
        Num expectedProfitOfClosedPositionAfter = DoubleNum.valueOf(-0.04).minus(expectedHoldingCostsAfter);
        Num expectedHoldingCostsBefore = DoubleNum.valueOf(2.0 * 4.0 * 0.001);
        Num expectedProfitOfClosedPositionBefore = DoubleNum.valueOf(-0.04).minus(expectedHoldingCostsBefore);

        assertNumEquals(DoubleNum.valueOf(-1.05), profitOfOpenPositionFinalAfter);
        assertNumEquals(DoubleNum.valueOf(-1.02), profitOfOpenPositionFinalBefore);
        assertNumEquals(expectedProfitOfClosedPositionAfter, profitOfClosedPositionFinalAfter);
        assertNumEquals(expectedProfitOfClosedPositionBefore, profitOfClosedPositionFinalBefore);
    }

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @Test
    public void spotPositionsKeepRealizedAndUnrealizedSplit() {
        for (NumFactory numFactory : factories()) {
            Trade entry = new BaseTrade(0, T0, numFactory.numOf(100), numFactory.one(), numFactory.zero(),
                    ExecutionSide.BUY, null, null);
            Trade exit = new BaseTrade(1, T0.plusSeconds(1), numFactory.numOf(110), numFactory.one(), numFactory.zero(),
                    ExecutionSide.SELL, null, null);

            Position closed = new Position(entry, exit, entry.getCostModel(), new ZeroCostModel());
            assertNumEquals(10, closed.getProfit());
            assertNumEquals(10, closed.getRealizedProfit(1));
            assertNumEquals(0, closed.getUnrealizedProfit(numFactory.numOf(110), 1));
            assertNumEquals(3, closed.getReturnOnMargin(numFactory.numOf(5), numFactory.numOf(110), 1));

            Trade costlyEntry = new BaseTrade(0, T0, numFactory.numOf(100), numFactory.one(), numFactory.one(),
                    ExecutionSide.BUY, null, null);
            Position open = new Position(costlyEntry, costlyEntry.getCostModel(), new ZeroCostModel());
            assertNumEquals(9, open.getProfit(0, numFactory.numOf(110)));
            assertNumEquals(-1, open.getRealizedProfit(0));
            assertNumEquals(10, open.getUnrealizedProfit(numFactory.numOf(110), 0));
            assertNumEquals(open.getProfit(0, numFactory.numOf(110)),
                    open.getUnrealizedProfit(numFactory.numOf(110), 0).plus(open.getRealizedProfit(0)));
        }
    }

    @Test
    public void partialFuturesHoldingCostUsesSettlementNotional() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = FuturesContract.builder()
                    .venue("CDE")
                    .symbol("BTC-PERP")
                    .productType(FuturesContract.ProductType.PERPETUAL)
                    .settlementType(FuturesContract.SettlementType.LINEAR)
                    .baseCurrency("BTC")
                    .quoteCurrency("USD")
                    .settlementCurrency("USD")
                    .contractSize(numFactory.numOf(10))
                    .build();
            TradeFill executed = TradeFill.builder()
                    .index(0)
                    .time(T0)
                    .price(numFactory.numOf(100))
                    .amount(numFactory.numOf(2))
                    .side(ExecutionSide.SELL)
                    .futuresContract(contract)
                    .fees(List.of())
                    .build();
            TradeFill future = TradeFill.builder()
                    .index(3)
                    .time(T0.plusSeconds(3))
                    .price(numFactory.numOf(110))
                    .amount(numFactory.one())
                    .side(ExecutionSide.SELL)
                    .futuresContract(contract)
                    .fees(List.of())
                    .build();
            Trade entry = Trade.fromFills(TradeType.SELL, List.of(executed, future), RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE,
                    new LinearBorrowingCostModel(0.01, LinearBorrowingCostModel.Applicability.BOTH));

            assertNumEquals(40, position.getHoldingCost(2));
        }
    }
}

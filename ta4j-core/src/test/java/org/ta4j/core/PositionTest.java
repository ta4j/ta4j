/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
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

    @BeforeEach
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

    @Test
    public void testCostModelEntryInconsistent() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Position(enter, exitDifferentType, new ZeroCostModel(), holdingModel);
        });
    }

    @Test
    public void testCostModelExitInconsistent() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Position(enter, exitDifferentType, transactionModel, holdingModel);
        });
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

    private static Stream<NumFactory> numFactories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    public void validNewOpenAndClosedPositionsRemainOperational(NumFactory factory) throws Exception {
        for (TradeType type : TradeType.values()) {
            Position restored = restore(
                    new Position(type, new LinearTransactionCostModel(0.01), new LinearBorrowingCostModel(0.01)));
            assertTrue(restored.isNew());
            assertTrue(restored.getTransactionCostModel() instanceof ZeroCostModel);
            assertTrue(restored.getHoldingCostModel() instanceof ZeroCostModel);
            restored.operate(1, factory.numOf(100), factory.one());
            restored = restore(restored);
            assertTrue(restored.isOpened());
            assertEquals(type, restored.getEntry().getType());
            assertNumEquals(0, restored.getPositionCost(3));
            assertNumEquals(0, restored.getHoldingCost(3));
            restored.operate(3, factory.numOf(110), factory.one());
            restored = restore(restored);
            assertTrue(restored.isClosed());
            assertEquals(type.complementType(), restored.getExit().getType());
            assertNumEquals(0, restored.getPositionCost());
            assertNumEquals(0, restored.getPositionCost(4));
            assertNumEquals(0, restored.getHoldingCost());
            assertNumEquals(0, restored.getHoldingCost(4));
            assertNumEquals(type == TradeType.BUY ? 10 : -10, restored.getProfit());
        }
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    public void constructorSupportedEarlierAndEqualExitIndicesRemainValid(NumFactory factory) throws Exception {
        for (TradeType type : TradeType.values()) {
            for (int exitIndex : new int[] { 2, 5 }) {
                Trade entry = new BaseTrade(5, type, factory.numOf(100), factory.one());
                Trade exit = new BaseTrade(exitIndex, type.complementType(), factory.numOf(110), factory.one());
                Position original = new Position(entry, exit);
                Position restored = restore(original);
                assertEquals(original, restored);
                assertEquals(exitIndex, restored.getExit().getIndex());
                assertNumEquals(0, restored.getPositionCost());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    public void serializableCustomTradeModelsRemainEffectiveAfterLoading(NumFactory factory) throws Exception {
        CostModel model = new SerializableFeeModel(2);
        Trade entry = new PersistentModelTrade(1, TradeType.BUY, factory.numOf(100), factory.one(), model);
        Trade exit = new PersistentModelTrade(3, TradeType.SELL, factory.numOf(110), factory.one(), model);
        Position closed = restore(new Position(entry, exit, model, new ZeroCostModel()));
        assertSame(closed.getEntry().getCostModel(), closed.getTransactionCostModel());
        assertNumEquals(4, closed.getPositionCost());
        assertNumEquals(4, closed.getPositionCost(4));

        Position open = restore(new Position(entry, model, new ZeroCostModel()));
        assertNumEquals(2, open.getPositionCost(3));
        open.operate(3, factory.numOf(110), factory.one());
        assertTrue(open.isClosed());
        assertNumEquals(2, open.getExit().getCost());
        assertNumEquals(4, open.getPositionCost());
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest
    @MethodSource("numFactories")
    public void mixedLiveAndBaseTradesRetainTheirRecordedFeeModel(NumFactory factory) throws Exception {
        TraceTestLogger capture = new TraceTestLogger();
        // Legacy-trade compatibility deliberately triggers its deprecation notice.
        capture.open();
        try {
            for (TradeType type : TradeType.values()) {
                for (boolean liveEntry : new boolean[] { false, true }) {
                    ExecutionSide entrySide = type == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
                    ExecutionSide exitSide = entrySide == ExecutionSide.BUY ? ExecutionSide.SELL : ExecutionSide.BUY;
                    Trade entry = liveEntry
                            ? new LiveTrade(1, java.time.Instant.EPOCH, factory.numOf(100), factory.one(),
                                    factory.numOf(2), entrySide, null, null)
                            : new BaseTrade(1, java.time.Instant.EPOCH, factory.numOf(100), factory.one(),
                                    factory.numOf(2), entrySide, null, null);
                    Trade exit = liveEntry
                            ? new BaseTrade(3, java.time.Instant.EPOCH, factory.numOf(110), factory.one(),
                                    factory.numOf(3), exitSide, null, null)
                            : new LiveTrade(3, java.time.Instant.EPOCH, factory.numOf(110), factory.one(),
                                    factory.numOf(3), exitSide, null, null);
                    Position original = new Position(entry, exit);
                    Position restored = restore(original);
                    assertTrue(restored.isClosed());
                    assertEquals(type, restored.getStartingType());
                    assertSame(RecordedTradeCostModel.INSTANCE, restored.getTransactionCostModel());
                    assertNumEquals(5, restored.getPositionCost());
                    assertNumEquals(2, restored.getPositionCost(1));
                    assertNumEquals(5, restored.getPositionCost(4));
                    assertNumEquals(type == TradeType.BUY ? 5 : -15, restored.getProfit());
                    assertNumEquals(original.getProfit(), restored.getProfit());
                }
            }
        } finally {
            capture.close();
        }
    }

    @Test
    public void deserializationRejectsNullStartingType() {
        assertThrows(InvalidObjectException.class, () -> restore(new SerializedPositionState(null, null, null)));
    }

    @Test
    public void deserializationRejectsExitWithoutEntry() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, null, trade(2, TradeType.SELL))));
    }

    @Test
    public void deserializationRejectsStartingTypeMismatch() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.SELL, trade(1, TradeType.BUY), null)));
    }

    @Test
    public void deserializationRejectsSameEntryAndExitTypes() {
        assertThrows(InvalidObjectException.class, () -> restore(
                new SerializedPositionState(TradeType.BUY, trade(1, TradeType.BUY), trade(2, TradeType.BUY))));
    }

    @Test
    public void deserializationRejectsNullCustomEntryType() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, trade(1, null), null)));
    }

    @Test
    public void deserializationRejectsNullCustomExitType() {
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, trade(1, TradeType.BUY), trade(2, null))));
    }

    @Test
    public void deserializationRejectsNullCustomEntryCostModel() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Trade entry = new PersistentModelTrade(1, TradeType.BUY, factory.numOf(100), factory.one(), null);
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, entry, null)));
    }

    @Test
    public void deserializationRejectsNullCustomExitCostModel() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Trade exit = new PersistentModelTrade(2, TradeType.SELL, factory.numOf(110), factory.one(), null);
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, trade(1, TradeType.BUY), exit)));
    }

    @Test
    public void deserializationRejectsMismatchedCustomCostModels() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Trade entry = new PersistentModelTrade(1, TradeType.BUY, factory.numOf(100), factory.one(),
                new SerializableFeeModel(2));
        Trade exit = new PersistentModelTrade(2, TradeType.SELL, factory.numOf(110), factory.one(),
                new SerializableFeeModel(3));
        assertThrows(InvalidObjectException.class,
                () -> restore(new SerializedPositionState(TradeType.BUY, entry, exit)));
    }

    @Test
    public void deserializationRejectsMissingSuperclassData() {
        assertThrows(InvalidObjectException.class, () -> restore(new LegacyPositionSubclass()));
    }

    private static Trade trade(int index, TradeType type) {
        NumFactory factory = DoubleNumFactory.getInstance();
        return new PersistentModelTrade(index, type, factory.numOf(100), factory.one(), new SerializableFeeModel(0));
    }

    private static Position restore(Object state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                if (descriptor.getName().equals(SerializedPositionState.class.getName())) {
                    return ObjectStreamClass.lookup(Position.class);
                }
                if (descriptor.getName().equals(LegacyPositionSubclass.class.getName())) {
                    return ObjectStreamClass.lookup(PositionSubclass.class);
                }
                return descriptor;
            }
        }) {
            return (Position) input.readObject();
        }
    }

    /**
     * Matches Position's persisted fields without mutating production internals.
     */
    private static final class SerializedPositionState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Trade entry;
        private final Trade exit;
        private final TradeType startingType;

        private SerializedPositionState(TradeType startingType, Trade entry, Trade exit) {
            this.startingType = startingType;
            this.entry = entry;
            this.exit = exit;
        }
    }

    private static final class LegacyPositionSubclass implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static final class PositionSubclass extends Position {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    private static final class PersistentModelTrade extends BaseTrade {
        @Serial
        private static final long serialVersionUID = 1L;
        private final TradeType savedType;
        private final CostModel savedModel;

        private PersistentModelTrade(int index, TradeType type, Num price, Num amount, CostModel model) {
            super(index, type == null ? TradeType.BUY : type, price, amount,
                    model == null ? new ZeroCostModel() : model);
            savedType = type;
            savedModel = model;
        }

        @Override
        public TradeType getType() {
            return savedType;
        }

        @Override
        public CostModel getCostModel() {
            return savedModel;
        }
    }

    private static final class SerializableFeeModel implements CostModel, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final int fee;

        private SerializableFeeModel(int fee) {
            this.fee = fee;
        }

        @Override
        public Num calculate(Position position, int finalIndex) {
            Num total = calculate(position.getEntry().getValue(), position.getEntry().getAmount());
            return position.getExit() == null ? total
                    : total.plus(calculate(position.getExit().getValue(), position.getExit().getAmount()));
        }

        @Override
        public Num calculate(Position position) {
            return calculate(position, position.getExit().getIndex());
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return price.getNumFactory().numOf(fee);
        }

        @Override
        public boolean equals(CostModel model) {
            return model instanceof SerializableFeeModel other && fee == other.fee;
        }
    }
}

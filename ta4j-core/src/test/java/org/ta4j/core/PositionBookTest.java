/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PositionBookTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();
    private final RecordedTradeCostModel transactionCostModel = RecordedTradeCostModel.INSTANCE;
    private final ZeroCostModel holdingCostModel = new ZeroCostModel();

    @Test
    public void constructorRejectsNullArguments() {
        var costModel = new ZeroCostModel();

        assertThrows(NullPointerException.class, () -> new PositionBook(null));
        assertThrows(NullPointerException.class,
                () -> new PositionBook(Trade.TradeType.BUY, null, costModel, costModel));
        assertThrows(NullPointerException.class,
                () -> new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO, null, costModel));
        assertThrows(NullPointerException.class,
                () -> new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO, costModel, null));
    }

    @Test
    public void recordsEntriesAndExitsUsingFifo() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO, transactionCostModel,
                holdingCostModel);

        book.recordEntry(0, buyTrade(0, numFactory.hundred(), numFactory.one(), null, null), 0L);
        book.recordEntry(1, buyTrade(1, numFactory.numOf(110), numFactory.one(), null, null), 1L);
        book.recordExit(2, sellTrade(2, numFactory.numOf(120), numFactory.one(), null, null), 2L);

        assertEquals(1, book.getPositions().size());
        var closed = book.getPositions().getFirst();
        assertNumEquals(numFactory.hundred(), closed.getEntry().getPricePerAsset());

        assertEquals(1, book.openLots().size());
        var net = book.getNetOpenPosition();
        assertNumEquals(numFactory.one(), net.amount());
        assertNumEquals(numFactory.numOf(110), net.averageEntryPrice());
    }

    @Test
    public void recordsEntriesAndExitsUsingLifo() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.LIFO, transactionCostModel,
                holdingCostModel);

        book.recordEntry(0, buyTrade(0, numFactory.hundred(), numFactory.one(), null, null), 0L);
        book.recordEntry(1, buyTrade(1, numFactory.numOf(110), numFactory.one(), null, null), 1L);
        book.recordExit(2, sellTrade(2, numFactory.numOf(120), numFactory.one(), null, null), 2L);

        assertEquals(1, book.getPositions().size());
        var closed = book.getPositions().getFirst();
        assertNumEquals(numFactory.numOf(110), closed.getEntry().getPricePerAsset());

        assertEquals(1, book.openLots().size());
        var net = book.getNetOpenPosition();
        assertNumEquals(numFactory.one(), net.amount());
        assertNumEquals(numFactory.hundred(), net.averageEntryPrice());
    }

    @Test
    public void recordsEntriesAndExitsUsingAverageCost() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.AVG_COST, transactionCostModel,
                holdingCostModel);

        book.recordEntry(0, buyTrade(0, numFactory.hundred(), numFactory.one(), null, null), 0L);
        book.recordEntry(1, buyTrade(1, numFactory.numOf(110), numFactory.one(), null, null), 1L);
        book.recordExit(2, sellTrade(2, numFactory.numOf(120), numFactory.one(), null, null), 2L);

        assertEquals(1, book.getPositions().size());
        var closed = book.getPositions().getFirst();
        assertNumEquals(numFactory.numOf(105), closed.getEntry().getPricePerAsset());

        var openLots = book.openLots();
        assertEquals(1, openLots.size());
        assertNumEquals(numFactory.one(), openLots.getFirst().amount());

        var net = book.getNetOpenPosition();
        assertNumEquals(numFactory.one(), net.amount());
        assertNumEquals(numFactory.numOf(105), net.averageEntryPrice());
    }

    @Test
    public void recordsEntriesAndExitsUsingSpecificId() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID, transactionCostModel,
                holdingCostModel);

        book.recordEntry(0, buyTrade(0, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"), 0L);
        book.recordEntry(1, buyTrade(1, numFactory.numOf(110), numFactory.one(), "order-2", "corr-2"), 1L);
        book.recordExit(2, sellTrade(2, numFactory.numOf(120), numFactory.one(), null, "corr-2"), 2L);

        assertEquals(1, book.getPositions().size());
        var closed = book.getPositions().getFirst();
        assertNumEquals(numFactory.numOf(110), closed.getEntry().getPricePerAsset());

        var openLots = book.openLots();
        assertEquals(1, openLots.size());
        assertEquals("order-1", openLots.getFirst().orderId());
    }

    @Test
    public void specificIdValidationsThrowErrors() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID, transactionCostModel,
                holdingCostModel);

        book.recordEntry(0, buyTrade(0, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"), 0L);

        assertThrows(IllegalStateException.class,
                () -> book.recordExit(1, sellTrade(1, numFactory.numOf(120), numFactory.one(), null, "corr-2"), 1L));
        assertThrows(IllegalStateException.class,
                () -> book.recordExit(1, sellTrade(1, numFactory.numOf(120), numFactory.two(), null, "corr-1"), 1L));
    }

    @Test
    public void exitWithoutOpenLotsThrows() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO, transactionCostModel,
                holdingCostModel);

        assertThrows(IllegalStateException.class,
                () -> book.recordExit(0, sellTrade(0, numFactory.hundred(), numFactory.one(), null, null), 0L));
    }

    @Test
    public void rejectsNullAndNonPositiveTrades() {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO, transactionCostModel,
                holdingCostModel);

        assertThrows(IllegalArgumentException.class, () -> book.recordEntry(0, null, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> book.recordEntry(0, buyTrade(0, numFactory.hundred(), numFactory.zero(), null, null), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> book.recordExit(0, sellTrade(0, numFactory.hundred(), numFactory.zero(), null, null), 0L));
    }

    @Test
    public void serializationRoundTripPreservesOpenAndClosedPositions() throws Exception {
        var book = new PositionBook(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO, transactionCostModel,
                holdingCostModel);

        book.recordEntry(0,
                buyTrade(0, numFactory.hundred(), numFactory.two(), "order-1", "corr-1", numFactory.numOf(0.1)), 0L);
        book.recordExit(1,
                sellTrade(1, numFactory.numOf(110), numFactory.one(), null, "corr-1", numFactory.numOf(0.05)), 1L);
        book.recordEntry(2,
                buyTrade(2, numFactory.numOf(120), numFactory.one(), "order-2", "corr-2", numFactory.numOf(0.2)), 2L);

        var restored = roundTrip(book);

        assertOpenLotsEqual(book.openLots(), restored.openLots());

        var closedOriginal = book.closedPositionsWithSequence();
        var closedRestored = restored.closedPositionsWithSequence();
        assertEquals(closedOriginal.size(), closedRestored.size());
        for (int i = 0; i < closedOriginal.size(); i++) {
            var expected = closedOriginal.get(i);
            var actual = closedRestored.get(i);
            assertEquals(expected.entrySequence(), actual.entrySequence());
            assertEquals(expected.exitSequence(), actual.exitSequence());
            assertTrue(expected.position().equals(actual.position()));
        }
    }

    private void assertOpenLotsEqual(List<PositionLot> expected, List<PositionLot> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            var expectedLot = expected.get(i);
            var actualLot = actual.get(i);
            assertEquals(expectedLot.entryIndex(), actualLot.entryIndex());
            assertEquals(expectedLot.entrySequence(), actualLot.entrySequence());
            assertEquals(expectedLot.entryTime(), actualLot.entryTime());
            assertNumEquals(expectedLot.entryPrice(), actualLot.entryPrice());
            assertNumEquals(expectedLot.amount(), actualLot.amount());
            assertNumEquals(expectedLot.fee(), actualLot.fee());
            assertEquals(expectedLot.orderId(), actualLot.orderId());
            assertEquals(expectedLot.correlationId(), actualLot.correlationId());
        }
    }

    private LiveTrade buyTrade(int index, Num price, Num amount, String orderId, String correlationId) {
        return buyTrade(index, price, amount, orderId, correlationId, null);
    }

    private LiveTrade buyTrade(int index, Num price, Num amount, String orderId, String correlationId, Num fee) {
        return new LiveTrade(index, Instant.EPOCH, price, amount, fee, ExecutionSide.BUY, orderId, correlationId);
    }

    private LiveTrade sellTrade(int index, Num price, Num amount, String orderId, String correlationId) {
        return sellTrade(index, price, amount, orderId, correlationId, null);
    }

    private LiveTrade sellTrade(int index, Num price, Num amount, String orderId, String correlationId, Num fee) {
        return new LiveTrade(index, Instant.EPOCH, price, amount, fee, ExecutionSide.SELL, orderId, correlationId);
    }

    private PositionBook roundTrip(PositionBook book) throws Exception {
        var outputStream = new ByteArrayOutputStream();
        try (var objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(book);
        }

        try (var objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            return (PositionBook) objectInputStream.readObject();
        }
    }
}

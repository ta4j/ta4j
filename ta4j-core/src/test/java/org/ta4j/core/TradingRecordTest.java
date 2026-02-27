/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import static org.ta4j.core.num.NaN.NaN;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.num.DoubleNumFactory;

public class TradingRecordTest {

    private TradingRecord emptyRecord, openedRecord, closedRecord;

    @Before
    public void setUp() {
        emptyRecord = new BaseTradingRecord();
        openedRecord = new BaseTradingRecord(Trade.buyAt(0, NaN, NaN), Trade.sellAt(3, NaN, NaN),
                Trade.buyAt(7, NaN, NaN));
        closedRecord = new BaseTradingRecord(Trade.buyAt(0, NaN, NaN), Trade.sellAt(3, NaN, NaN),
                Trade.buyAt(7, NaN, NaN), Trade.sellAt(8, NaN, NaN));
    }

    @Test
    public void getCurrentPosition() {
        assertTrue(emptyRecord.getCurrentPosition().isNew());
        assertTrue(openedRecord.getCurrentPosition().isOpened());
        assertTrue(closedRecord.getCurrentPosition().isNew());
    }

    @Test
    public void operate() {
        TradingRecord record = new BaseTradingRecord();

        record.operate(1);
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(0, record.getPositionCount());
        assertNull(record.getLastPosition());
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastTrade());
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastTrade(Trade.TradeType.BUY));
        assertNull(record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastEntry());
        assertNull(record.getLastExit());

        record.operate(3);
        assertTrue(record.getCurrentPosition().isNew());
        assertEquals(1, record.getPositionCount());
        assertEquals(new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(3, NaN, NaN)), record.getLastPosition());
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastTrade());
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastEntry());
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastExit());

        record.operate(5);
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(1, record.getPositionCount());
        assertEquals(new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(3, NaN, NaN)), record.getLastPosition());
        assertEquals(Trade.buyAt(5, NaN, NaN), record.getLastTrade());
        assertEquals(Trade.buyAt(5, NaN, NaN), record.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.buyAt(5, NaN, NaN), record.getLastEntry());
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastExit());
    }

    @Test
    public void isClosed() {
        assertTrue(emptyRecord.isClosed());
        assertFalse(openedRecord.isClosed());
        assertTrue(closedRecord.isClosed());
    }

    @Test
    public void getPositionCount() {
        assertEquals(0, emptyRecord.getPositionCount());
        assertEquals(1, openedRecord.getPositionCount());
        assertEquals(2, closedRecord.getPositionCount());
    }

    @Test
    public void getLastPosition() {
        assertNull(emptyRecord.getLastPosition());
        assertEquals(new Position(Trade.buyAt(0, NaN, NaN), Trade.sellAt(3, NaN, NaN)), openedRecord.getLastPosition());
        assertEquals(new Position(Trade.buyAt(7, NaN, NaN), Trade.sellAt(8, NaN, NaN)), closedRecord.getLastPosition());
    }

    @Test
    public void getLastTrade() {
        // Last trade
        assertNull(emptyRecord.getLastTrade());
        assertEquals(Trade.buyAt(7, NaN, NaN), openedRecord.getLastTrade());
        assertEquals(Trade.sellAt(8, NaN, NaN), closedRecord.getLastTrade());
        // Last BUY trade
        assertNull(emptyRecord.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.buyAt(7, NaN, NaN), openedRecord.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.buyAt(7, NaN, NaN), closedRecord.getLastTrade(Trade.TradeType.BUY));
        // Last SELL trade
        assertNull(emptyRecord.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.sellAt(3, NaN, NaN), openedRecord.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.sellAt(8, NaN, NaN), closedRecord.getLastTrade(Trade.TradeType.SELL));
    }

    @Test
    public void getLastEntryExit() {
        // Last entry
        assertNull(emptyRecord.getLastEntry());
        assertEquals(Trade.buyAt(7, NaN, NaN), openedRecord.getLastEntry());
        assertEquals(Trade.buyAt(7, NaN, NaN), closedRecord.getLastEntry());
        // Last exit
        assertNull(emptyRecord.getLastExit());
        assertEquals(Trade.sellAt(3, NaN, NaN), openedRecord.getLastExit());
        assertEquals(Trade.sellAt(8, NaN, NaN), closedRecord.getLastExit());
    }

    @Test
    public void createRecordFromSingleClosedPosition() {
        var position = new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(4, NaN, NaN));

        var record = new BaseTradingRecord(position);

        assertTrue(record.getCurrentPosition().isNew());
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
        assertEquals(position, record.getLastPosition());
        assertEquals(Trade.sellAt(4, NaN, NaN), record.getLastTrade());
    }

    @Test
    public void createRecordFromSingleOpenPosition() {
        var position = new Position(Trade.TradeType.BUY);
        position.operate(2, NaN, NaN);

        var record = new BaseTradingRecord(position);

        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(0, record.getPositionCount());
        assertNull(record.getLastPosition());
        assertEquals(Trade.buyAt(2, NaN, NaN), record.getLastEntry());
    }

    @Test
    public void createRecordFromMultiplePositions() {
        var first = new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(3, NaN, NaN));
        var second = new Position(Trade.sellAt(5, NaN, NaN), Trade.buyAt(7, NaN, NaN));

        var record = new BaseTradingRecord(List.of(first, second));

        assertTrue(record.getCurrentPosition().isNew());
        assertEquals(2, record.getPositionCount());
        assertEquals(second, record.getLastPosition());
        assertEquals(4, record.getTrades().size());
    }

    @Test
    public void createRecordFromDeserializedPositionDefaultsCostModels() throws Exception {
        var position = new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(2, NaN, NaN));

        var deserialized = roundTrip(position);

        assertTrue(deserialized.getTransactionCostModel() instanceof ZeroCostModel);
        assertTrue(deserialized.getHoldingCostModel() instanceof ZeroCostModel);

        var record = new BaseTradingRecord(deserialized);

        assertTrue(record.getTransactionCostModel() instanceof ZeroCostModel);
        assertTrue(record.getHoldingCostModel() instanceof ZeroCostModel);
        assertEquals(1, record.getPositionCount());
    }

    @Test
    public void baseTradingRecordRejectsLiveTrades() {
        var numFactory = DoubleNumFactory.getInstance();
        var trade = new LiveTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), null, ExecutionSide.BUY,
                null, null);

        assertThrows(IllegalArgumentException.class, () -> new BaseTradingRecord(trade));
    }

    @Test
    public void baseTradingRecordRejectsEmptyTradesArray() {
        assertThrows(IllegalArgumentException.class, () -> new BaseTradingRecord(new Trade[0]));
    }

    @Test
    public void operateWithPrebuiltTradePreservesExecutionFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = new BaseTradingRecord();
        Trade aggregatedEntry = new AggregatedTrade(Trade.TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.one()),
                        new TradeFill(2, numFactory.numOf(101), numFactory.one())));
        Trade aggregatedExit = new AggregatedTrade(Trade.TradeType.SELL,
                List.of(new TradeFill(4, numFactory.numOf(110), numFactory.two())));

        record.operate(aggregatedEntry);
        assertFalse(record.isClosed());
        assertEquals(2, record.getLastTrade().getFills().size());

        record.operate(aggregatedExit);
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
        assertEquals(2, record.getLastPosition().getEntry().getFills().size());
        assertEquals(1, record.getLastPosition().getExit().getFills().size());
    }

    @Test
    public void enterAndExitWithTradeRespectOpenCloseState() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = new BaseTradingRecord();
        Trade firstEntry = Trade.buyAt(1, numFactory.hundred(), numFactory.one());
        Trade duplicateEntry = Trade.buyAt(2, numFactory.numOf(101), numFactory.one());
        Trade exit = Trade.sellAt(3, numFactory.numOf(110), numFactory.one());

        assertTrue(record.enter(firstEntry));
        assertFalse(record.enter(duplicateEntry));
        assertTrue(record.exit(exit));
        assertFalse(record.exit(Trade.sellAt(4, numFactory.numOf(120), numFactory.one())));
    }

    @Test
    public void tradeDefaultOperationsRejectNullTrades() {
        TradingRecord record = new BaseTradingRecord();

        assertThrows(NullPointerException.class, () -> record.operate((Trade) null));
        assertThrows(NullPointerException.class, () -> record.enter((Trade) null));
        assertThrows(NullPointerException.class, () -> record.exit((Trade) null));
    }

    @Test
    public void defaultOperateTradeFallsBackToScalarOperateForSingleFillTrade() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        Trade singleFillTrade = new AggregatedTrade(Trade.TradeType.BUY,
                List.of(new TradeFill(2, numFactory.hundred(), numFactory.one())));

        record.operate(singleFillTrade);

        assertFalse(record.isClosed());
        assertEquals(2, record.getLastTrade().getIndex());
        assertEquals(numFactory.hundred(), record.getLastTrade().getPricePerAsset());
        assertEquals(numFactory.one(), record.getLastTrade().getAmount());
        assertEquals(1, record.getLastTrade().getFills().size());
    }

    @Test
    public void defaultOperateTradeRejectsMultiFillTrade() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        Trade multiFillTrade = new AggregatedTrade(Trade.TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.one()),
                        new TradeFill(2, numFactory.numOf(101), numFactory.one())));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> record.operate(multiFillTrade));

        assertEquals("This TradingRecord implementation must override operate(Trade) to preserve multi-fill trades",
                exception.getMessage());
    }

    private Position roundTrip(Position position) throws Exception {
        var outputStream = new ByteArrayOutputStream();
        try (var objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(position);
        }

        try (var objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            return (Position) objectInputStream.readObject();
        }
    }

    private TradingRecord newTradingRecordUsingDefaultOperateImplementation() {
        return new DefaultOperateTradingRecordImpl();
    }

    private interface DefaultOperateTradingRecord extends TradingRecord {

        @Override
        default void operate(Trade trade) {
            TradingRecord.super.operate(trade);
        }
    }

    private static final class DefaultOperateTradingRecordImpl extends BaseTradingRecord
            implements DefaultOperateTradingRecord {

        @Override
        public void operate(Trade trade) {
            DefaultOperateTradingRecord.super.operate(trade);
        }
    }
}

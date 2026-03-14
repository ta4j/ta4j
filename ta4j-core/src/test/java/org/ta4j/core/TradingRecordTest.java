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
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.analysis.cost.CostModel;

public class TradingRecordTest {

    private final DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
    private TradingRecord emptyRecord, openedRecord, closedRecord;

    @Before
    public void setUp() {
        emptyRecord = new BaseTradingRecord();
        openedRecord = new BaseTradingRecord(buyAt(0), sellAt(3), buyAt(7));
        closedRecord = new BaseTradingRecord(buyAt(0), sellAt(3), buyAt(7), sellAt(8));
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

        record.operate(1, numFactory.hundred(), numFactory.one());
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(0, record.getPositionCount());
        assertNull(record.getLastPosition());
        assertEquals(buyAt(1), record.getLastTrade());
        assertEquals(buyAt(1), record.getLastTrade(Trade.TradeType.BUY));
        assertNull(record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(buyAt(1), record.getLastEntry());
        assertNull(record.getLastExit());

        record.operate(3, numFactory.numOf(110), numFactory.one());
        assertTrue(record.getCurrentPosition().isNew());
        assertEquals(1, record.getPositionCount());
        assertEquals(new Position(buyAt(1), sellAt(3)), record.getLastPosition());
        assertEquals(sellAt(3), record.getLastTrade());
        assertEquals(buyAt(1), record.getLastTrade(Trade.TradeType.BUY));
        assertEquals(sellAt(3), record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(buyAt(1), record.getLastEntry());
        assertEquals(sellAt(3), record.getLastExit());

        record.operate(5, numFactory.hundred(), numFactory.one());
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(1, record.getPositionCount());
        assertEquals(new Position(buyAt(1), sellAt(3)), record.getLastPosition());
        assertEquals(buyAt(5), record.getLastTrade());
        assertEquals(buyAt(5), record.getLastTrade(Trade.TradeType.BUY));
        assertEquals(sellAt(3), record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(buyAt(5), record.getLastEntry());
        assertEquals(sellAt(3), record.getLastExit());
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
        assertEquals(new Position(buyAt(0), sellAt(3)), openedRecord.getLastPosition());
        assertEquals(new Position(buyAt(7), sellAt(8)), closedRecord.getLastPosition());
    }

    @Test
    public void getLastTrade() {
        // Last trade
        assertNull(emptyRecord.getLastTrade());
        assertEquals(buyAt(7), openedRecord.getLastTrade());
        assertEquals(sellAt(8), closedRecord.getLastTrade());
        // Last BUY trade
        assertNull(emptyRecord.getLastTrade(Trade.TradeType.BUY));
        assertEquals(buyAt(7), openedRecord.getLastTrade(Trade.TradeType.BUY));
        assertEquals(buyAt(7), closedRecord.getLastTrade(Trade.TradeType.BUY));
        // Last SELL trade
        assertNull(emptyRecord.getLastTrade(Trade.TradeType.SELL));
        assertEquals(sellAt(3), openedRecord.getLastTrade(Trade.TradeType.SELL));
        assertEquals(sellAt(8), closedRecord.getLastTrade(Trade.TradeType.SELL));
    }

    @Test
    public void getLastEntryExit() {
        // Last entry
        assertNull(emptyRecord.getLastEntry());
        assertEquals(buyAt(7), openedRecord.getLastEntry());
        assertEquals(buyAt(7), closedRecord.getLastEntry());
        // Last exit
        assertNull(emptyRecord.getLastExit());
        assertEquals(sellAt(3), openedRecord.getLastExit());
        assertEquals(sellAt(8), closedRecord.getLastExit());
    }

    @Test
    public void createRecordFromSingleClosedPosition() {
        var position = new Position(buyAt(1), sellAt(4));

        var record = new BaseTradingRecord(position);

        assertTrue(record.getCurrentPosition().isNew());
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
        assertEquals(position, record.getLastPosition());
        assertEquals(sellAt(4), record.getLastTrade());
    }

    @Test
    public void createRecordFromSingleOpenPosition() {
        var position = new Position(Trade.TradeType.BUY);
        position.operate(2, numFactory.hundred(), numFactory.one());

        var record = new BaseTradingRecord(position);

        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(0, record.getPositionCount());
        assertNull(record.getLastPosition());
        assertEquals(buyAt(2), record.getLastEntry());
    }

    @Test
    public void createRecordFromMultiplePositions() {
        var first = new Position(buyAt(1), sellAt(3));
        var second = new Position(sellAt(5), buyAt(7));

        var record = new BaseTradingRecord(List.of(first, second));

        assertTrue(record.getCurrentPosition().isNew());
        assertEquals(2, record.getPositionCount());
        assertEquals(second, record.getLastPosition());
        assertEquals(4, record.getTrades().size());
    }

    @Test
    public void createRecordFromDeserializedPositionDefaultsCostModels() throws Exception {
        var position = new Position(buyAt(1), sellAt(2));

        var deserialized = roundTrip(position);

        assertTrue(deserialized.getTransactionCostModel() instanceof ZeroCostModel);
        assertTrue(deserialized.getHoldingCostModel() instanceof ZeroCostModel);

        var record = new BaseTradingRecord(deserialized);

        assertTrue(record.getTransactionCostModel() instanceof ZeroCostModel);
        assertTrue(record.getHoldingCostModel() instanceof ZeroCostModel);
        assertEquals(1, record.getPositionCount());
    }

    @Test
    public void baseTradingRecordAcceptsTradeBasedFills() {
        var trade = new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), null, ExecutionSide.BUY,
                null, null);

        var record = new BaseTradingRecord(trade);
        assertEquals(1, record.getTrades().size());
        assertEquals(trade.getType(), record.getLastTrade().getType());
    }

    @Test
    public void baseTradingRecordRejectsEmptyTradesArray() {
        assertThrows(IllegalArgumentException.class, () -> new BaseTradingRecord(new Trade[0]));
    }

    @Test
    public void operateWithPrebuiltTradePreservesExecutionFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = new BaseTradingRecord();
        Trade aggregatedEntry = Trade.fromFills(Trade.TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.one()),
                        new TradeFill(2, numFactory.numOf(101), numFactory.one())));
        Trade aggregatedExit = Trade.fromFills(Trade.TradeType.SELL,
                List.of(new TradeFill(4, numFactory.numOf(110), numFactory.two())));

        record.operate(aggregatedEntry);
        assertFalse(record.isClosed());
        assertEquals(2, record.getTrades().size());
        assertEquals(1, record.getLastTrade().getFills().size());

        record.operate(aggregatedExit);
        assertTrue(record.isClosed());
        assertEquals(2, record.getPositionCount());
        int totalEntryFills = record.getPositions()
                .stream()
                .map(Position::getEntry)
                .mapToInt(trade -> trade.getFills().size())
                .sum();
        int totalExitFills = record.getPositions()
                .stream()
                .map(Position::getExit)
                .mapToInt(trade -> trade.getFills().size())
                .sum();
        assertEquals(2, totalEntryFills);
        assertEquals(2, totalExitFills);
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
    public void enterAndExitWithTradeRejectWrongTradeTypes() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = new BaseTradingRecord();

        assertThrows(IllegalArgumentException.class,
                () -> record.enter(Trade.sellAt(1, numFactory.hundred(), numFactory.one())));

        assertTrue(record.enter(Trade.buyAt(1, numFactory.hundred(), numFactory.one())));

        assertThrows(IllegalArgumentException.class,
                () -> record.exit(Trade.buyAt(2, numFactory.numOf(110), numFactory.one())));
    }

    @Test
    public void tradeDefaultOperationsRejectNullTrades() {
        TradingRecord record = new BaseTradingRecord();

        assertThrows(NullPointerException.class, () -> record.operate((Trade) null));
        assertThrows(NullPointerException.class, () -> record.operate((TradeFill) null));
        assertThrows(NullPointerException.class, () -> record.enter((Trade) null));
        assertThrows(NullPointerException.class, () -> record.exit((Trade) null));
    }

    @Test
    public void defaultOperateFillDelegatesThroughTradeFactory() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        TradeFill fill = new TradeFill(2, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                null, ExecutionSide.BUY, "order-1", "corr-1");

        record.operate(fill);

        assertFalse(record.isClosed());
        assertEquals(2, record.getLastTrade().getIndex());
        assertEquals(numFactory.hundred(), record.getLastTrade().getPricePerAsset());
        assertEquals(numFactory.one(), record.getLastTrade().getAmount());
        assertEquals(1, record.getLastTrade().getFills().size());
    }

    @Test
    public void defaultOperateFillRejectsMissingSide() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        TradeFill fillWithoutSide = new TradeFill(2, null, numFactory.hundred(), numFactory.one(), null, null, null,
                null);

        assertThrows(IllegalArgumentException.class, () -> record.operate(fillWithoutSide));
    }

    @Test
    public void defaultOperateTradeFallsBackToScalarOperateForSingleFillTrade() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        Trade singleFillTrade = Trade.fromFills(Trade.TradeType.BUY,
                List.of(new TradeFill(2, numFactory.hundred(), numFactory.one())));

        record.operate(singleFillTrade);

        assertFalse(record.isClosed());
        assertEquals(2, record.getLastTrade().getIndex());
        assertEquals(numFactory.hundred(), record.getLastTrade().getPricePerAsset());
        assertEquals(numFactory.one(), record.getLastTrade().getAmount());
        assertEquals(1, record.getLastTrade().getFills().size());
    }

    @Test
    public void defaultOperateTradeFallsBackWhenCustomTradeProvidesNoFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        Trade customTradeWithoutFills = new BaseTrade(3, Trade.TradeType.BUY, numFactory.hundred(), numFactory.one()) {
            @Override
            public List<TradeFill> getFills() {
                return List.of();
            }
        };

        record.operate(customTradeWithoutFills);

        assertFalse(record.isClosed());
        assertEquals(3, record.getLastTrade().getIndex());
        assertEquals(numFactory.hundred(), record.getLastTrade().getPricePerAsset());
        assertEquals(numFactory.one(), record.getLastTrade().getAmount());
        assertEquals(1, record.getLastTrade().getFills().size());
    }

    @Test
    public void defaultOperateTradeRejectsMultiFillTrade() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradingRecord record = newTradingRecordUsingDefaultOperateImplementation();
        Trade multiFillTrade = Trade.fromFills(Trade.TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.one()),
                        new TradeFill(2, numFactory.numOf(101), numFactory.one())));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> record.operate(multiFillTrade));

        assertEquals("This TradingRecord implementation must override operate(Trade) to preserve multi-fill trades",
                exception.getMessage());
    }

    @Test
    public void defaultOpenPositionMethodsRemainSafeForLegacyTradingRecordImplementations() {
        TradingRecord record = new LegacyTradingRecordStub();

        record.operate(1, numFactory.hundred(), numFactory.one());

        assertFalse(record.isClosed());
        assertTrue(record.getOpenPositions().isEmpty());
        assertNull(record.getNetOpenPosition());
        assertNull(record.getRecordedTotalFees());
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

    private Trade buyAt(int index) {
        return Trade.buyAt(index, numFactory.hundred(), numFactory.one());
    }

    private Trade sellAt(int index) {
        return Trade.sellAt(index, numFactory.numOf(110), numFactory.one());
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

    private static final class LegacyTradingRecordStub implements TradingRecord {

        private final BaseTradingRecord delegate = new BaseTradingRecord();

        @Override
        public Trade.TradeType getStartingType() {
            return delegate.getStartingType();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public void operate(int index, Num price, Num amount) {
            delegate.operate(index, price, amount);
        }

        @Override
        public boolean enter(int index, Num price, Num amount) {
            return delegate.enter(index, price, amount);
        }

        @Override
        public boolean exit(int index, Num price, Num amount) {
            return delegate.exit(index, price, amount);
        }

        @Override
        public CostModel getTransactionCostModel() {
            return delegate.getTransactionCostModel();
        }

        @Override
        public CostModel getHoldingCostModel() {
            return delegate.getHoldingCostModel();
        }

        @Override
        public List<Position> getPositions() {
            return delegate.getPositions();
        }

        @Override
        public Position getCurrentPosition() {
            return delegate.getCurrentPosition();
        }

        @Override
        public List<Trade> getTrades() {
            return delegate.getTrades();
        }

        @Override
        public Integer getStartIndex() {
            return delegate.getStartIndex();
        }

        @Override
        public Integer getEndIndex() {
            return delegate.getEndIndex();
        }
    }
}

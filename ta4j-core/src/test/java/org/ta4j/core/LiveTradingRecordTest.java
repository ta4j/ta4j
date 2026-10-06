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
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class LiveTradingRecordTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void defaultConstructorInitializesDefaults() {
        LiveTradingRecord record = new LiveTradingRecord();

        assertEquals(TradeType.BUY, record.getStartingType());
        assertNull(record.getStartIndex());
        assertNull(record.getEndIndex());
        assertTrue(record.getPositions().isEmpty());
    }

    @Test
    void constructorWithStartingTypeInitializesCorrectly() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.SELL);

        assertEquals(TradeType.SELL, record.getStartingType());
        assertNull(record.getStartIndex());
        assertNull(record.getEndIndex());
    }

    @Test
    void fullConstructorOverridesTransactionCostModelAndSetsBounds() {
        CostModel customTxCost = new FixedTransactionCostModel(10.0);
        CostModel holdingCost = new LinearBorrowingCostModel(0.01);
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, customTxCost,
                holdingCost, 2, 10);

        assertEquals(TradeType.BUY, record.getStartingType());
        assertEquals(2, record.getStartIndex());
        assertEquals(10, record.getEndIndex());

        LiveTrade entry = new LiveTrade(2, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.5), ExecutionSide.BUY, "ord-1", "corr-1");
        record.recordFill(entry);

        Position current = record.getCurrentPosition();
        assertNotNull(current);
        assertEquals(holdingCost, current.getHoldingCostModel());
        assertEquals(RecordedTradeCostModel.INSTANCE, current.getTransactionCostModel());
    }

    @Test
    void recordFillWithLiveTradeAutoIndexes() {
        LiveTradingRecord record = new LiveTradingRecord();
        LiveTrade entry = new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.5), ExecutionSide.BUY, "ord-1", "corr-1");
        LiveTrade exit = new LiveTrade(1, Instant.parse("2025-01-01T00:01:00Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.5), ExecutionSide.SELL, "ord-2", "corr-1");

        record.recordFill(entry);
        assertEquals(0, record.getLastTrade().getIndex());
        assertTrue(record.getCurrentPosition().isOpened());

        record.recordFill(exit);
        assertEquals(1, record.getLastTrade().getIndex());
        assertEquals(1, record.getPositions().size());
        assertEquals(numFactory.one(), record.getRecordedTotalFees());
    }

    @Test
    void recordFillWithExplicitIndexAndLiveTrade() {
        LiveTradingRecord record = new LiveTradingRecord();
        LiveTrade entry = new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, "ord-1", "corr-1");

        record.recordFill(15, entry);

        assertEquals(15, record.getLastTrade().getIndex());
    }

    @Test
    void recordFillNullTradeThrowsNullPointerException() {
        LiveTradingRecord record = new LiveTradingRecord();

        assertThrows(NullPointerException.class, () -> record.recordFill(null));
        assertThrows(NullPointerException.class, () -> record.recordFill(5, null));
    }

    @Test
    void recordExecutionFillExplicitSideAndTime() {
        LiveTradingRecord record = new LiveTradingRecord();
        ExecutionFill fill = createExecutionFill(5, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.BUY, "ord-1", "corr-1");

        record.recordExecutionFill(fill);

        Trade lastTrade = record.getLastTrade();
        assertNotNull(lastTrade);
        assertEquals(5, lastTrade.getIndex());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), lastTrade.getTime());
        assertEquals(numFactory.hundred(), lastTrade.getPricePerAsset());
        assertEquals(numFactory.one(), lastTrade.getAmount());
        assertEquals(numFactory.numOf(0.2), lastTrade.getCost());
        assertEquals("ord-1", lastTrade.getOrderId());
        assertEquals("corr-1", lastTrade.getCorrelationId());
    }

    @Test
    void recordExecutionFillNullFillThrowsNullPointerException() {
        LiveTradingRecord record = new LiveTradingRecord();

        assertThrows(NullPointerException.class, () -> record.recordExecutionFill((ExecutionFill) null));
    }

    @Test
    void recordExecutionFillNullTimeDefaultsToEpoch() {
        LiveTradingRecord record = new LiveTradingRecord();
        ExecutionFill fill = createExecutionFill(0, null, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, "ord-1", "corr-1");

        record.recordExecutionFill(fill);

        assertEquals(Instant.EPOCH, record.getLastTrade().getTime());
    }

    @Test
    void recordExecutionFillNullSideResolvesCorrectlyUnopenedBuy() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY);
        ExecutionFill fill = createExecutionFill(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), null, "ord-1", "corr-1");

        record.recordExecutionFill(fill);

        assertEquals(ExecutionSide.BUY, record.getLastTrade().getFills().getFirst().side());
        assertTrue(record.getCurrentPosition().isOpened());
    }

    @Test
    void recordExecutionFillNullSideResolvesCorrectlyUnopenedSell() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.SELL);
        ExecutionFill fill = createExecutionFill(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), null, "ord-1", "corr-1");

        record.recordExecutionFill(fill);

        assertEquals(ExecutionSide.SELL, record.getLastTrade().getFills().getFirst().side());
        assertTrue(record.getCurrentPosition().isOpened());
    }

    @Test
    void recordExecutionFillNullSideResolvesCorrectlyOpenedLong() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY);
        ExecutionFill entryFill = createExecutionFill(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, "ord-1", "corr-1");
        record.recordExecutionFill(entryFill);

        ExecutionFill exitFill = createExecutionFill(1, Instant.parse("2025-01-01T00:01:00Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.zero(), null, "ord-2", "corr-1");
        record.recordExecutionFill(exitFill);

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        assertEquals(TradeType.BUY, positions.getFirst().getEntry().getType());
        assertEquals(TradeType.SELL, positions.getFirst().getExit().getType());
    }

    @Test
    void recordExecutionFillNullSideResolvesCorrectlyOpenedShort() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.SELL);
        ExecutionFill entryFill = createExecutionFill(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.SELL, "ord-1", "corr-1");
        record.recordExecutionFill(entryFill);

        ExecutionFill exitFill = createExecutionFill(1, Instant.parse("2025-01-01T00:01:00Z"), numFactory.numOf(90),
                numFactory.one(), numFactory.zero(), null, "ord-2", "corr-1");
        record.recordExecutionFill(exitFill);

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        assertEquals(TradeType.SELL, positions.getFirst().getEntry().getType());
        assertEquals(TradeType.BUY, positions.getFirst().getExit().getType());
    }

    @Test
    void rehydratePreservesRecordedTradeCostModel() {
        LiveTradingRecord record = new LiveTradingRecord();

        record.rehydrate(new ZeroCostModel());

        LiveTrade trade = new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, "ord-1", "corr-1");
        record.recordFill(trade);
        assertEquals(RecordedTradeCostModel.INSTANCE, record.getCurrentPosition().getTransactionCostModel());

        record.rehydrate(new FixedTransactionCostModel(5.0), new LinearBorrowingCostModel(0.02));
        assertEquals(RecordedTradeCostModel.INSTANCE, record.getCurrentPosition().getTransactionCostModel());
    }

    @Test
    void serializationRoundTrip() throws Exception {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new LinearBorrowingCostModel(0.01), null, null);

        LiveTrade entry = new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, "ord-1", "corr-1");
        record.recordFill(entry);

        byte[] serializedData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(record);
            oos.flush();
            serializedData = baos.toByteArray();
        }

        LiveTradingRecord rehydrated;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            rehydrated = (LiveTradingRecord) ois.readObject();
        }

        assertNotNull(rehydrated);
        assertEquals(TradeType.BUY, rehydrated.getStartingType());
        assertTrue(rehydrated.getCurrentPosition().isOpened());
        assertEquals(numFactory.hundred(), rehydrated.getCurrentPosition().averageEntryPrice());

        LiveTrade exit = new LiveTrade(1, Instant.parse("2025-01-01T00:01:00Z"), numFactory.numOf(120),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.SELL, "ord-2", "corr-1");
        rehydrated.recordFill(exit);

        assertEquals(1, rehydrated.getPositions().size());
        assertEquals(numFactory.numOf(0.2), rehydrated.getRecordedTotalFees());
    }

    private ExecutionFill createExecutionFill(int index, Instant time, Num price, Num amount, Num fee,
            ExecutionSide side, String orderId, String correlationId) {
        return new ExecutionFill() {
            @Override
            public int index() {
                return index;
            }

            @Override
            public Instant time() {
                return time;
            }

            @Override
            public Num price() {
                return price;
            }

            @Override
            public Num amount() {
                return amount;
            }

            @Override
            public Num fee() {
                return fee;
            }

            @Override
            public ExecutionSide side() {
                return side;
            }

            @Override
            public String orderId() {
                return orderId;
            }

            @Override
            public String correlationId() {
                return correlationId;
            }
        };
    }
}

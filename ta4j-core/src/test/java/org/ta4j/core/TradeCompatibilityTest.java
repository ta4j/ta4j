/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class TradeCompatibilityTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void baseTradeAndBaseTradingRecordRemainUsable() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        BaseTrade trade = new BaseTrade(7, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf("0.25"), ExecutionSide.BUY, "order-1", "corr-1");

        record.operate(Trade.fromFill(new TradeFill(-1, trade.getTime(), trade.getPricePerAsset(), trade.getAmount(),
                trade.getCost(), ExecutionSide.BUY, trade.getOrderId(), trade.getCorrelationId())));

        Trade recordedTrade = record.getLastTrade();
        assertNotNull(recordedTrade);
        assertEquals(0, recordedTrade.getIndex());
        assertEquals(TradeType.BUY, recordedTrade.getType());
        assertEquals(numFactory.numOf("0.25"), record.getTotalFees());
    }

    @Test
    void tradeFillRoutesThroughBaseTradingRecord() {
        BaseTradingRecord record = new BaseTradingRecord();
        TradeFill fill = new TradeFill(-1, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf("0.10"), ExecutionSide.BUY, "order-2", "corr-2");

        record.operate(fill);

        Trade recordedTrade = record.getLastTrade();
        assertNotNull(recordedTrade);
        assertEquals(0, recordedTrade.getIndex());
        assertEquals("order-2", recordedTrade.getOrderId());
        assertEquals(numFactory.numOf("0.10"), record.getRecordedTotalFees());
    }

    @Test
    void tradeFactoriesCreateScalarTrades() {
        Trade trade = Trade.buyAt(3, numFactory.hundred(), numFactory.numOf(2));

        assertEquals(TradeType.BUY, trade.getType());
        assertEquals(3, trade.getIndex());
        assertEquals(numFactory.numOf(2), trade.getAmount());
        assertEquals(numFactory.zero(), trade.getCost());
    }

    @Test
    void tradingRecordOpenPositionContractStartsEmpty() {
        TradingRecord record = new BaseTradingRecord();
        assertTrue(record.getPositions().isEmpty());
        assertTrue(record.getOpenPositions().isEmpty());
        assertFalse(record.getCurrentPosition().isOpened());
    }

    @Test
    void tradeFillExposesExecutionMetadata() {
        TradeFill fill = new TradeFill(4, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf("0.05"), ExecutionSide.SELL, "order-3", "corr-3");

        assertEquals(numFactory.numOf("0.05"), fill.fee());
        assertEquals(4, fill.index());
        assertEquals("corr-3", fill.correlationId());
        assertEquals(ExecutionSide.SELL, fill.side());
    }

    @Test
    void baseTradingRecordPreservesRecordedFeeSemantics() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, RecordedTradeCostModel.INSTANCE,
                new ZeroCostModel());
        record.operate(new TradeFill(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf("0.20"), ExecutionSide.BUY, null, null));
        record.operate(new TradeFill(1, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf("0.30"), ExecutionSide.SELL, null, null));

        assertEquals(RecordedTradeCostModel.INSTANCE, record.getTransactionCostModel());
        assertEquals(numFactory.numOf("0.50"), record.getRecordedTotalFees());
        assertFalse(record.getPositions().isEmpty());
    }
}

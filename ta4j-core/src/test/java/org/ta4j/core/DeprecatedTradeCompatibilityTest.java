/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class DeprecatedTradeCompatibilityTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void liveTradeAndLiveTradingRecordRemainUsable() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        LiveTrade trade = new LiveTrade(7, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf("0.25"), ExecutionSide.BUY, "order-1", "corr-1");

        record.recordFill(trade);

        Trade recordedTrade = record.getLastTrade();
        assertNotNull(recordedTrade);
        assertEquals(0, recordedTrade.getIndex());
        assertEquals(TradeType.BUY, recordedTrade.getType());
        assertEquals(numFactory.numOf("0.25"), record.getTotalFees());
    }

    @Test
    void executionFillContractStillRoutesThroughLiveTradingRecord() {
        LiveTradingRecord record = new LiveTradingRecord();
        ExecutionFill fill = new ExecutionFill() {
            @Override
            public Instant time() {
                return Instant.parse("2025-01-01T00:00:00Z");
            }

            @Override
            public Num price() {
                return numFactory.hundred();
            }

            @Override
            public Num amount() {
                return numFactory.one();
            }

            @Override
            public Num fee() {
                return numFactory.numOf("0.10");
            }

            @Override
            public ExecutionSide side() {
                return ExecutionSide.BUY;
            }

            @Override
            public String orderId() {
                return "order-2";
            }

            @Override
            public String correlationId() {
                return "corr-2";
            }

            @Override
            public int index() {
                return -1;
            }
        };

        record.recordExecutionFill(fill);

        Trade recordedTrade = record.getLastTrade();
        assertNotNull(recordedTrade);
        assertEquals(0, recordedTrade.getIndex());
        assertEquals("order-2", recordedTrade.getOrderId());
        assertEquals(numFactory.numOf("0.10"), record.getRecordedTotalFees());
    }

    @Test
    void simulatedTradeFactoriesStillExist() {
        SimulatedTrade trade = SimulatedTrade.buyAt(3, numFactory.hundred(), numFactory.numOf(2));

        assertEquals(TradeType.BUY, trade.getType());
        assertEquals(3, trade.getIndex());
        assertEquals(numFactory.numOf(2), trade.getAmount());
        assertEquals(numFactory.zero(), trade.getCost());
    }

    @Test
    void positionLedgerContractStillAvailable() {
        PositionLedger ledger = new LiveTradingRecord();
        assertTrue(ledger.getPositions().isEmpty());
        assertTrue(ledger.getOpenPositions().isEmpty());
        assertEquals(null, ledger.getNetOpenPosition());
    }

    @Test
    void liveTradeStillImplementsExecutionFill() {
        ExecutionFill fill = new LiveTrade(4, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf("0.05"), ExecutionSide.SELL, "order-3", "corr-3");

        assertTrue(fill.hasFee());
        assertEquals(4, fill.index());
        assertEquals("corr-3", fill.intentId());
        assertEquals(ExecutionSide.SELL, fill.side());
    }

    @Test
    void liveTradingRecordPreservesRecordedFeeSemantics() {
        LiveTradingRecord record = new LiveTradingRecord();
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf("0.20"), ExecutionSide.BUY, null, null));
        record.recordFill(new LiveTrade(1, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf("0.30"), ExecutionSide.SELL, null, null));

        assertEquals(RecordedTradeCostModel.INSTANCE, record.getTransactionCostModel());
        assertEquals(numFactory.numOf("0.50"), record.getRecordedTotalFees());
        assertFalse(record.getPositions().isEmpty());
    }
}

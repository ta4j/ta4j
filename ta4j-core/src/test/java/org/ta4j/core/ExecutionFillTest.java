/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class ExecutionFillTest {

    @Test
    void liveTradeImplementsExecutionFill() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        ExecutionFill fill = new LiveTrade(5, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null,
                ExecutionSide.BUY, "order-1", "intent-1");

        assertEquals(5, fill.index());
        assertEquals("intent-1", fill.intentId());
        assertEquals(ExecutionSide.BUY, fill.side());
    }

    @Test
    void recordExecutionFillAcceptsExecutionFill() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        ExecutionFill fill = new TestFill(Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, ExecutionSide.BUY,
                null, "intent-2");

        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordExecutionFill(fill);

        assertEquals(1, record.getTrades().size());
        assertEquals("intent-2", record.getTrades().get(0).getCorrelationId());
    }

    @Test
    void tradeFillCanBeConsumedAsExecutionFill() {
        Num price = DoubleNumFactory.getInstance().numOf(101);
        Num amount = DoubleNumFactory.getInstance().numOf(2);
        ExecutionFill fill = new TradeFill(7, Instant.parse("2025-01-01T00:00:00Z"), price, amount, ExecutionSide.BUY);

        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordExecutionFill(fill);

        assertEquals(1, record.getTrades().size());
        assertEquals(7, record.getTrades().getFirst().getIndex());
        assertEquals(price, record.getTrades().getFirst().getPricePerAsset());
    }

    @Test
    void recordExecutionFillInfersMissingSideAndTime() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        ExecutionFill fillWithoutMetadata = new ExecutionFill() {
            @Override
            public Num price() {
                return price;
            }

            @Override
            public Num amount() {
                return amount;
            }
        };

        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordExecutionFill(fillWithoutMetadata);
        record.recordExecutionFill(fillWithoutMetadata);

        assertEquals(2, record.getTrades().size());
        assertEquals(TradeType.BUY, record.getTrades().get(0).getType());
        assertEquals(TradeType.SELL, record.getTrades().get(1).getType());
        assertEquals(Instant.EPOCH, record.getTrades().get(0).getTime());
        assertEquals(Instant.EPOCH, record.getTrades().get(1).getTime());
    }

    private record TestFill(Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
            String correlationId) implements ExecutionFill {
    }
}

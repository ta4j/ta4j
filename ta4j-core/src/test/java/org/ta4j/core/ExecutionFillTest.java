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
    void recordFillAcceptsExecutionFill() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        ExecutionFill fill = new TestFill(Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, ExecutionSide.BUY,
                null, "intent-2");

        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill);

        assertEquals(1, record.getTrades().size());
        assertEquals("intent-2", record.getTrades().get(0).getCorrelationId());
    }

    private record TestFill(Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
            String correlationId) implements ExecutionFill {
    }
}

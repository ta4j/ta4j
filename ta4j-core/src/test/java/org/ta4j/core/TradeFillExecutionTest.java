/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class TradeFillExecutionTest {

    @Test
    void operateAcceptsTradeBuiltFromTradeFill() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        TradeFill fill = new TradeFill(5, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, ExecutionSide.BUY,
                "order-1", "corr-1");

        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(Trade.fromFill(fill));

        assertEquals(1, record.getTrades().size());
        assertEquals("corr-1", record.getTrades().get(0).getCorrelationId());
    }

    @Test
    void operateKeepsExplicitIndexFromTradeFill() {
        Num price = DoubleNumFactory.getInstance().numOf(101);
        Num amount = DoubleNumFactory.getInstance().numOf(2);
        TradeFill fill = new TradeFill(7, Instant.parse("2025-01-01T00:00:00Z"), price, amount, ExecutionSide.BUY);

        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(Trade.fromFill(fill));

        assertEquals(1, record.getTrades().size());
        assertEquals(7, record.getTrades().getFirst().getIndex());
        assertEquals(price, record.getTrades().getFirst().getPricePerAsset());
    }

    @Test
    void tradeFromFillRequiresExplicitSide() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        TradeFill fillWithoutSide = new TradeFill(-1, null, price, amount, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> Trade.fromFill(fillWithoutSide));
    }

    @Test
    void operateFromTradeFillDefaultsMissingTimeToEpoch() {
        Num price = DoubleNumFactory.getInstance().numOf(100);
        Num amount = DoubleNumFactory.getInstance().numOf(1);
        TradeFill fillWithoutTime = new TradeFill(-1, null, price, amount, null, ExecutionSide.BUY, null, null);

        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(Trade.fromFill(fillWithoutTime));

        assertEquals(1, record.getTrades().size());
        assertEquals(Instant.EPOCH, record.getTrades().getFirst().getTime());
    }
}

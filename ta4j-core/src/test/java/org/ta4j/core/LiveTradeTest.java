/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

@SuppressWarnings("removal")
class LiveTradeTest {

    private static final DoubleNumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void compatibilityAccessorsMirrorLegacyRecordContract() {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        Num price = NUM_FACTORY.hundred();
        Num amount = NUM_FACTORY.two();
        Num fee = NUM_FACTORY.numOf(0.2);

        LiveTrade trade = new LiveTrade(5, time, price, amount, fee, ExecutionSide.BUY, "order-5", "corr-5");

        assertEquals(5, trade.index());
        assertEquals(time, trade.time());
        assertNumEquals(price, trade.price());
        assertNumEquals(amount, trade.amount());
        assertNumEquals(fee, trade.fee());
        assertEquals(ExecutionSide.BUY, trade.side());
        assertEquals("order-5", trade.orderId());
        assertEquals("corr-5", trade.correlationId());
        assertTrue(trade.hasFee());
        assertTrue(trade.getCostModel().equals(RecordedTradeCostModel.INSTANCE));
    }

    @Test
    void withIndexReturnsLiveTradeAndPreservesFields() {
        LiveTrade original = new LiveTrade(1, Instant.parse("2025-01-01T00:00:00Z"), NUM_FACTORY.hundred(),
                NUM_FACTORY.one(), NUM_FACTORY.numOf(0.1), ExecutionSide.SELL, "order-1", "corr-1");

        LiveTrade reindexed = original.withIndex(9);

        assertEquals(9, reindexed.index());
        assertEquals(original.time(), reindexed.time());
        assertNumEquals(original.price(), reindexed.price());
        assertNumEquals(original.amount(), reindexed.amount());
        assertNumEquals(original.fee(), reindexed.fee());
        assertEquals(original.side(), reindexed.side());
        assertEquals(original.orderId(), reindexed.orderId());
        assertEquals(original.correlationId(), reindexed.correlationId());
    }

    @Test
    void nullFeeDefaultsToZero() {
        LiveTrade trade = new LiveTrade(0, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(), null,
                ExecutionSide.BUY, null, null);

        assertNumEquals(NUM_FACTORY.zero(), trade.fee());
        assertFalse(trade.hasFee());
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class SimulatedTradeTest {

    private static final DoubleNumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void liveConstructorCarriesMetadataAndFeeIntoTradeAndFill() {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        Num price = NUM_FACTORY.hundred();
        Num amount = NUM_FACTORY.two();
        Num fee = NUM_FACTORY.numOf(0.4);

        SimulatedTrade trade = new SimulatedTrade(3, time, price, amount, fee, ExecutionSide.BUY, "order-1", "corr-1");

        assertEquals(TradeType.BUY, trade.getType());
        assertEquals(3, trade.getIndex());
        assertEquals(time, trade.getTime());
        assertEquals("order-1", trade.getOrderId());
        assertEquals("corr-1", trade.getCorrelationId());
        assertNumEquals(price, trade.getPricePerAsset());
        assertNumEquals(amount, trade.getAmount());
        assertNumEquals(fee, trade.getCost());
        assertNumEquals(NUM_FACTORY.numOf(100.2), trade.getNetPrice());
        assertEquals(1, trade.getFills().size());
        assertEquals(3, trade.getFills().getFirst().index());
        assertEquals(time, trade.getFills().getFirst().time());
        assertEquals(ExecutionSide.BUY, trade.getFills().getFirst().side());
        assertNumEquals(fee, trade.getFills().getFirst().fee());
    }

    @Test
    void liveConstructorDefaultsNullFeeToZero() {
        SimulatedTrade trade = new SimulatedTrade(1, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(), null,
                ExecutionSide.SELL, null, null);

        assertNumEquals(NUM_FACTORY.zero(), trade.getCost());
        assertNumEquals(NUM_FACTORY.hundred(), trade.getNetPrice());
        assertNumEquals(NUM_FACTORY.zero(), trade.getFills().getFirst().fee());
    }

    @Test
    void withIndexCopiesMetadataAndPreservesFee() {
        SimulatedTrade original = new SimulatedTrade(2, Instant.parse("2025-01-01T00:00:00Z"), NUM_FACTORY.hundred(),
                NUM_FACTORY.two(), NUM_FACTORY.numOf(0.2), ExecutionSide.BUY, "order-2", "corr-2");

        SimulatedTrade reindexed = original.withIndex(9);

        assertEquals(9, reindexed.getIndex());
        assertEquals(original.getTime(), reindexed.getTime());
        assertEquals(original.getOrderId(), reindexed.getOrderId());
        assertEquals(original.getCorrelationId(), reindexed.getCorrelationId());
        assertEquals(1, reindexed.getFills().size());
        assertEquals(9, reindexed.getFills().getFirst().index());
        assertNumEquals(original.getCost(), reindexed.getCost());
    }

    @Test
    void withIndexAfterSerializationPreservesRecordedFee() throws Exception {
        SimulatedTrade original = new SimulatedTrade(4, Instant.parse("2025-01-01T00:00:00Z"), NUM_FACTORY.hundred(),
                NUM_FACTORY.one(), NUM_FACTORY.numOf(0.3), ExecutionSide.BUY, "order-4", "corr-4");
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(original);
            objectOutput.flush();
            serialized = output.toByteArray();
        }

        SimulatedTrade restored;
        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            restored = (SimulatedTrade) objectInput.readObject();
        }

        SimulatedTrade reindexed = restored.withIndex(10);

        assertEquals(10, reindexed.getIndex());
        assertNumEquals(NUM_FACTORY.numOf(0.3), reindexed.getCost());
        assertNumEquals(NUM_FACTORY.numOf(100.3), reindexed.getNetPrice());
    }

    @Test
    void withIndexRejectsNegativeIndex() {
        SimulatedTrade trade = new SimulatedTrade(0, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(),
                NUM_FACTORY.zero(), ExecutionSide.BUY, null, null);

        assertThrows(IllegalArgumentException.class, () -> trade.withIndex(-1));
    }

    @Test
    void fromFillsWithRecordedCostModelUsesFillFees() {
        TradeFill firstFill = new TradeFill(1, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(),
                NUM_FACTORY.numOf(0.1), ExecutionSide.BUY, "order-1", "corr-1");
        TradeFill secondFill = new TradeFill(2, Instant.EPOCH, NUM_FACTORY.numOf(102), NUM_FACTORY.one(),
                NUM_FACTORY.numOf(0.2), ExecutionSide.BUY, "order-1", "corr-1");

        Trade trade = Trade.fromFills(TradeType.BUY, List.of(firstFill, secondFill), RecordedTradeCostModel.INSTANCE);

        assertNumEquals(NUM_FACTORY.numOf(0.3), trade.getCost());
        assertNumEquals(NUM_FACTORY.numOf(101.15), trade.getNetPrice());
        assertEquals(2, trade.getFills().size());
    }
}

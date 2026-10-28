/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class BaseTradeTest {

    private static final DoubleNumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void liveConstructorCarriesMetadataAndFeeIntoTradeAndFill() {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        Num price = NUM_FACTORY.hundred();
        Num amount = NUM_FACTORY.two();
        Num fee = NUM_FACTORY.numOf(0.4);

        BaseTrade trade = new BaseTrade(3, time, price, amount, fee, ExecutionSide.BUY, "order-1", "corr-1");

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
    void compatibilityAccessorsMirrorLegacyRecordContract() {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        Num price = NUM_FACTORY.hundred();
        Num amount = NUM_FACTORY.two();
        Num fee = NUM_FACTORY.numOf(0.2);

        BaseTrade trade = new BaseTrade(5, time, price, amount, fee, ExecutionSide.BUY, "order-5", "corr-5");

        assertEquals(5, trade.getIndex());
        assertEquals(time, trade.time());
        assertNumEquals(price, trade.price());
        assertNumEquals(amount, trade.amount());
        assertNumEquals(fee, trade.fee());
        assertEquals(ExecutionSide.BUY, trade.side());
        assertEquals("order-5", trade.orderId());
        assertEquals("corr-5", trade.correlationId());
        assertTrue(trade.getCostModel().equals(RecordedTradeCostModel.INSTANCE));
    }

    @Test
    void liveConstructorDefaultsNullFeeToZero() {
        BaseTrade trade = new BaseTrade(1, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(), null,
                ExecutionSide.SELL, null, null);

        assertNumEquals(NUM_FACTORY.zero(), trade.getCost());
        assertNumEquals(NUM_FACTORY.hundred(), trade.getNetPrice());
        assertNumEquals(NUM_FACTORY.zero(), trade.getFills().getFirst().fee());
        assertNumEquals(NUM_FACTORY.zero(), trade.fee());
        assertTrue(trade.fee().isZero());
    }

    @Test
    void withIndexCopiesMetadataAndPreservesFee() {
        BaseTrade original = new BaseTrade(2, Instant.parse("2025-01-01T00:00:00Z"), NUM_FACTORY.hundred(),
                NUM_FACTORY.two(), NUM_FACTORY.numOf(0.2), ExecutionSide.BUY, "order-2", "corr-2");

        BaseTrade reindexed = original.withIndex(9);

        assertEquals(9, reindexed.getIndex());
        assertEquals(original.getTime(), reindexed.getTime());
        assertEquals(original.getOrderId(), reindexed.getOrderId());
        assertEquals(original.getCorrelationId(), reindexed.getCorrelationId());
        assertEquals(1, reindexed.getFills().size());
        assertEquals(9, reindexed.getFills().getFirst().index());
        assertNumEquals(original.getCost(), reindexed.getCost());
        assertEquals(original.time(), reindexed.time());
        assertNumEquals(original.price(), reindexed.price());
        assertNumEquals(original.amount(), reindexed.amount());
        assertNumEquals(original.fee(), reindexed.fee());
        assertEquals(original.side(), reindexed.side());
        assertEquals(original.orderId(), reindexed.orderId());
        assertEquals(original.correlationId(), reindexed.correlationId());
    }

    @Test
    void withIndexPreservesFillOffsetsForMultiFillTrades() {
        TradeFill firstFill = new TradeFill(2, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(),
                NUM_FACTORY.numOf(0.1), ExecutionSide.BUY, "order-1", "corr-1");
        TradeFill secondFill = new TradeFill(4, Instant.EPOCH.plusSeconds(60), NUM_FACTORY.numOf(102),
                NUM_FACTORY.one(), NUM_FACTORY.numOf(0.2), ExecutionSide.BUY, "order-1", "corr-1");
        BaseTrade original = new BaseTrade(TradeType.BUY, List.of(firstFill, secondFill),
                RecordedTradeCostModel.INSTANCE);

        BaseTrade reindexed = original.withIndex(10);

        assertEquals(10, reindexed.getIndex());
        assertEquals(List.of(10, 12), reindexed.getFills().stream().map(TradeFill::index).toList());
        assertNumEquals(original.getCost(), reindexed.getCost());
    }

    @Test
    void withIndexAfterSerializationPreservesRecordedFee() throws Exception {
        BaseTrade original = new BaseTrade(4, Instant.parse("2025-01-01T00:00:00Z"), NUM_FACTORY.hundred(),
                NUM_FACTORY.one(), NUM_FACTORY.numOf(0.3), ExecutionSide.BUY, "order-4", "corr-4");
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(original);
            objectOutput.flush();
            serialized = output.toByteArray();
        }

        BaseTrade restored;
        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            restored = (BaseTrade) objectInput.readObject();
        }

        BaseTrade reindexed = restored.withIndex(10);

        assertEquals(10, reindexed.getIndex());
        assertNumEquals(NUM_FACTORY.numOf(0.3), reindexed.getCost());
        assertNumEquals(NUM_FACTORY.numOf(100.3), reindexed.getNetPrice());
    }

    @Test
    void withIndexRejectsNegativeIndex() {
        BaseTrade trade = new BaseTrade(0, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(), NUM_FACTORY.zero(),
                ExecutionSide.BUY, null, null);

        assertThrows(IllegalArgumentException.class, () -> trade.withIndex(-1));
    }

    @Test
    void fromFillsWithRecordedCostModelUsesFillFees() {
        TradeFill firstFill = new TradeFill(1, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(),
                NUM_FACTORY.numOf(0.1), ExecutionSide.BUY, "order-1", "corr-1");
        TradeFill secondFill = new TradeFill(2, Instant.EPOCH, NUM_FACTORY.numOf(102), NUM_FACTORY.one(),
                NUM_FACTORY.numOf(0.2), ExecutionSide.BUY, "order-1", "corr-1");

        Trade trade = Trade.fromFills(TradeType.BUY, List.of(firstFill, secondFill), RecordedTradeCostModel.INSTANCE);

        assertNumEquals(NUM_FACTORY.numOf(0.30000000000000004), trade.getCost());
        assertNumEquals(NUM_FACTORY.numOf(101.15), trade.getNetPrice());
        assertEquals(2, trade.getFills().size());
    }

    @Test
    void fromFillsRejectsMismatchedFillSide() {
        TradeFill sellFill = new TradeFill(1, Instant.EPOCH, NUM_FACTORY.hundred(), NUM_FACTORY.one(),
                NUM_FACTORY.zero(), ExecutionSide.SELL, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> Trade.fromFills(TradeType.BUY, List.of(sellFill), RecordedTradeCostModel.INSTANCE));
    }

    @Test
    void fromFillsUsesEarliestFillMetadataAsIdentityAnchor() {
        Instant laterTime = Instant.parse("2025-01-01T00:05:00Z");
        Instant earlierTime = Instant.parse("2025-01-01T00:01:00Z");
        TradeFill laterFill = new TradeFill(5, laterTime, NUM_FACTORY.hundred(), NUM_FACTORY.one(), NUM_FACTORY.zero(),
                ExecutionSide.BUY, "order-later", "corr-later");
        TradeFill earlierFill = new TradeFill(2, earlierTime, NUM_FACTORY.numOf(101), NUM_FACTORY.one(),
                NUM_FACTORY.zero(), ExecutionSide.BUY, "order-earlier", "corr-earlier");

        Trade trade = Trade.fromFills(TradeType.BUY, List.of(laterFill, earlierFill), RecordedTradeCostModel.INSTANCE);

        assertEquals(2, trade.getIndex());
        assertEquals(earlierTime, trade.getTime());
        assertEquals("order-earlier", trade.getOrderId());
        assertEquals("corr-earlier", trade.getCorrelationId());
    }

    @Test
    public void zeroAmountTradesSurviveSerialization() throws Exception {
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            for (Trade trade : List.of(Trade.buyAt(0, factory.hundred(), factory.zero()),
                    Trade.sellAt(0, factory.hundred(), factory.zero()))) {
                BaseTrade restored = copyTrade((BaseTrade) trade);
                assertEquals(trade.getType(), restored.getType());
                assertNumEquals(factory.zero(), restored.getAmount());
                assertNumEquals(factory.hundred(), restored.getNetPrice());
            }
        }
    }

    @Test
    public void scalarTradeWithDeferredIndexAndNegativeAmountSurvivesSerialization() throws Exception {
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            Trade trade = Trade.buyAt(-1, factory.hundred(), factory.minusOne());
            BaseTrade restored = copyTrade((BaseTrade) trade);
            assertEquals(-1, restored.getIndex());
            assertNumEquals(factory.minusOne(), restored.getAmount());
            assertNumEquals(trade.getNetPrice(), restored.getNetPrice());
        }
    }

    @Test
    public void deserializationValidatesInvariants() throws Exception {
        SerializedTradeState state = new SerializedTradeState();
        assertEquals(1, restoreTradeState(state).getIndex());

        state.index = -2;
        assertInvalidState(state, "Index must be at least -1");
        state.index = 1;
        state.type = null;
        assertInvalidState(state, "Type must not be null");
        state.type = TradeType.BUY;
        state.pricePerAsset = null;
        assertInvalidState(state, "Price per asset must not be null");
        state.pricePerAsset = NUM_FACTORY.hundred();
        state.amount = null;
        assertInvalidState(state, "Amount must not be null");
        state.amount = NUM_FACTORY.one();
        state.fills = null;
        assertInvalidState(state, "Fills list must not be null");
        state.fills = List.of();
        state.cost = null;
        assertInvalidState(state, "Cost must not be null");
        state.cost = NUM_FACTORY.zero();
        state.netPrice = null;
        assertInvalidState(state, "Net price must not be null");
    }

    private void assertInvalidState(SerializedTradeState state, String message) {
        InvalidObjectException failure = assertThrows(InvalidObjectException.class, () -> restoreTradeState(state));
        assertEquals(message, failure.getMessage());
    }

    private BaseTrade restoreTradeState(SerializedTradeState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedTradeState.class.getName())
                        ? ObjectStreamClass.lookup(BaseTrade.class)
                        : descriptor;
            }
        }) {
            return (BaseTrade) input.readObject();
        }
    }

    /**
     * Serialized BaseTrade fields, allowing malformed input without mutating
     * production objects.
     */
    private static final class SerializedTradeState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private int index = 1;
        private TradeType type = TradeType.BUY;
        private Num pricePerAsset = NUM_FACTORY.hundred();
        private Num netPrice = NUM_FACTORY.hundred();
        private Num amount = NUM_FACTORY.one();
        private List<TradeFill> fills = List.of();
        private Instant time;
        private ExecutionSide side;
        private String orderId;
        private String correlationId;
        private Num cost = NUM_FACTORY.zero();
    }

    private BaseTrade copyTrade(BaseTrade original) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(original);
            objectOutput.flush();
            serialized = output.toByteArray();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (BaseTrade) objectInput.readObject();
        }
    }
}

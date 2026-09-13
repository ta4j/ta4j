/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class TradeFillTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @Test
    public void storesFillAttributes() {
        for (NumFactory factory : factories()) {
            TradeFill fill = new TradeFill(3, factory.hundred(), factory.two());

            assertEquals(3, fill.index());
            assertEquals(factory.hundred(), fill.price());
            assertEquals(factory.two(), fill.amount());
            assertEquals(factory.zero(), fill.fee());
        }
    }

    @Test
    public void storesOptionalMetadataWhenProvided() {
        for (NumFactory factory : factories()) {
            Instant time = Instant.parse("2025-01-01T00:00:00Z");
            TradeFill fill = new TradeFill(4, time, factory.hundred(), factory.one(), factory.numOf(0.2),
                    ExecutionSide.BUY, "order-1", "corr-1");

            assertEquals(time, fill.time());
            assertEquals(ExecutionSide.BUY, fill.side());
            assertEquals("order-1", fill.orderId());
            assertEquals("corr-1", fill.correlationId());
            assertEquals(factory.numOf(0.2), fill.fee());
        }
    }

    @Test
    public void sideAndTimeConstructorKeepsMetadataAndDefaultsFeeToZero() {
        for (NumFactory factory : factories()) {
            Instant time = Instant.parse("2025-01-02T00:00:00Z");
            TradeFill fill = new TradeFill(5, time, factory.numOf(110), factory.one(), ExecutionSide.SELL);

            assertEquals(time, fill.time());
            assertEquals(ExecutionSide.SELL, fill.side());
            assertEquals(factory.zero(), fill.fee());
        }
    }

    @Test
    public void supportsNegativeIndexForDeferredAssignment() {
        TradeFill fill = new TradeFill(-1, numFactory.hundred(), numFactory.one());

        assertEquals(-1, fill.index());
    }

    @Test
    public void rejectsIndexBelowDeferredAssignmentSentinel() {
        assertThrows(IllegalArgumentException.class, () -> new TradeFill(-2, numFactory.hundred(), numFactory.one()));
    }

    @Test
    public void rejectsNullPriceOrAmount() {
        assertThrows(NullPointerException.class, () -> new TradeFill(1, null, numFactory.one()));
        assertThrows(NullPointerException.class, () -> new TradeFill(1, numFactory.one(), null));
    }

    @Test
    public void futuresFillWithoutAnExecutionTimestampIsRejected() {
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();

        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> TradeFill.builder()
                        .index(0)
                        .price(numFactory.numOf(50_000))
                        .amount(numFactory.numOf(2))
                        .side(ExecutionSide.BUY)
                        .futuresContract(contract)
                        .fees(List.of())
                        .build());
        assertEquals("time", failure.getMessage());
    }

    @Test
    public void supportsSerializationRoundTrip() throws Exception {
        for (NumFactory factory : factories()) {
            TradeFill original = new TradeFill(3, factory.hundred(), factory.two());

            byte[] data;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(original);
                objectOutput.flush();
                data = output.toByteArray();
            }

            TradeFill restored;
            try (ByteArrayInputStream input = new ByteArrayInputStream(data);
                    ObjectInputStream objectInput = new ObjectInputStream(input)) {
                restored = (TradeFill) objectInput.readObject();
            }

            assertEquals(original, restored);
        }
    }

    @Test
    public void forTradeCarriesTheTradeInstrument() {
        TradeFill labelled = TradeFill.builder()
                .index(2)
                .time(Instant.EPOCH)
                .price(numFactory.hundred())
                .amount(numFactory.one())
                .side(ExecutionSide.BUY)
                .instrument("BTC-USD")
                .build();
        Trade trade = Trade.fromFills(TradeType.BUY, List.of(labelled), RecordedTradeCostModel.INSTANCE);

        TradeFill mirror = TradeFill.forTrade(trade, ExecutionSide.BUY);

        assertEquals("BTC-USD", mirror.instrument());
    }
}
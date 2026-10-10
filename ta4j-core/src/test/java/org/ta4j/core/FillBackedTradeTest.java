/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class FillBackedTradeTest extends AbstractIndicatorTest<BarSeries, Num> {

    public FillBackedTradeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void computesWeightedAveragePriceAndTotalAmount() {
        TradeFill firstFill = new TradeFill(2, numFactory.hundred(), numFactory.two());
        TradeFill secondFill = new TradeFill(3, numFactory.numOf(110), numFactory.one());
        Trade trade = Trade.fromFills(TradeType.BUY, List.of(firstFill, secondFill));

        assertEquals(TradeType.BUY, trade.getType());
        assertEquals(2, trade.getIndex());
        assertNumEquals(numFactory.three(), trade.getAmount());
        assertNumEquals(numFactory.numOf(103.3333333333), trade.getPricePerAsset(), 0.0001);
        assertEquals(List.of(firstFill, secondFill), trade.getFills());
    }

    @Test
    public void rejectsEmptyFillCollections() {
        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(TradeType.BUY, List.of()));
    }

    @Test
    public void rejectsNanFillPrice() {
        TradeFill fill = new TradeFill(1, NaN.NaN, numFactory.one());
        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(TradeType.BUY, List.of(fill)));
    }

    @Test
    public void rejectsNonPositiveFillAmount() {
        TradeFill zeroAmountFill = new TradeFill(1, numFactory.hundred(), numFactory.zero());
        TradeFill negativeAmountFill = new TradeFill(1, numFactory.hundred(), numFactory.minusOne());

        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(TradeType.BUY, List.of(zeroAmountFill)));
        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(TradeType.BUY, List.of(negativeAmountFill)));
    }

    @Test
    public void usesEarliestFillIndexWhenFillsAreUnordered() {
        Trade trade = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(5, numFactory.hundred(), numFactory.one()),
                new TradeFill(2, numFactory.numOf(101), numFactory.one())));

        assertEquals(2, trade.getIndex());
    }

    @Test
    public void serializationPreservesFills() throws Exception {
        Trade original = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.one()),
                        new TradeFill(2, numFactory.numOf(101), numFactory.one())));

        byte[] data;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(original);
            objectOutput.flush();
            data = output.toByteArray();
        }

        Trade restored;
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            restored = (Trade) objectInput.readObject();
        }

        assertEquals(2, restored.getFills().size());
        assertNumEquals(original.getPricePerAsset(), restored.getPricePerAsset());
        assertNumEquals(original.getAmount(), restored.getAmount());
    }

    @Test
    public void singleFillMetadataIsPreserved() {
        Instant fillTime = Instant.parse("2025-01-01T00:00:00Z");
        TradeFill fill = new TradeFill(3, fillTime, numFactory.hundred(), numFactory.one(), numFactory.numOf(0.1),
                ExecutionSide.BUY, "order-1", "corr-1");

        Trade trade = Trade.fromFills(TradeType.BUY, List.of(fill));

        assertEquals(1, trade.getFills().size());
        assertEquals(fill, trade.getFills().getFirst());
    }

    @Test
    public void usesRecordedFeesByDefault() {
        TradeFill firstFill = new TradeFill(1, null, numFactory.hundred(), numFactory.one(), numFactory.numOf(0.2),
                null, null, null);
        TradeFill secondFill = new TradeFill(2, null, numFactory.numOf(110), numFactory.two(), numFactory.numOf(0.3),
                null, null, null);

        Trade trade = Trade.fromFills(TradeType.BUY, List.of(firstFill, secondFill));

        assertNumEquals(numFactory.numOf(0.5), trade.getCost());
        assertNumEquals(numFactory.numOf(106.8333333333), trade.getNetPrice(), 0.0001);
    }
}

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
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TradeFillTest extends AbstractIndicatorTest<BarSeries, Num> {

    public TradeFillTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void storesFillAttributes() {
        TradeFill fill = new TradeFill(3, numFactory.hundred(), numFactory.two());

        assertEquals(3, fill.index());
        assertEquals(numFactory.hundred(), fill.price());
        assertEquals(numFactory.two(), fill.amount());
        assertEquals(numFactory.zero(), fill.fee());
    }

    @Test
    public void storesOptionalMetadataWhenProvided() {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        TradeFill fill = new TradeFill(4, time, numFactory.hundred(), numFactory.one(), numFactory.numOf(0.2),
                ExecutionSide.BUY, "order-1", "corr-1");

        assertEquals(time, fill.time());
        assertEquals(ExecutionSide.BUY, fill.side());
        assertEquals("order-1", fill.orderId());
        assertEquals("corr-1", fill.correlationId());
        assertEquals(numFactory.numOf(0.2), fill.fee());
    }

    @Test
    public void sideAndTimeConstructorKeepsMetadataAndDefaultsFeeToZero() {
        Instant time = Instant.parse("2025-01-02T00:00:00Z");
        TradeFill fill = new TradeFill(5, time, numFactory.numOf(110), numFactory.one(), ExecutionSide.SELL);

        assertEquals(time, fill.time());
        assertEquals(ExecutionSide.SELL, fill.side());
        assertEquals(numFactory.zero(), fill.fee());
    }

    @Test
    public void rejectsNullPriceOrAmount() {
        assertThrows(NullPointerException.class, () -> new TradeFill(1, null, numFactory.one()));
        assertThrows(NullPointerException.class, () -> new TradeFill(1, numFactory.one(), null));
    }

    @Test
    public void supportsSerializationRoundTrip() throws Exception {
        TradeFill original = new TradeFill(3, numFactory.hundred(), numFactory.two());

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

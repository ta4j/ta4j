/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class BaseBarTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();
    private final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
    private final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");

    private static List<NumFactory> numFactories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @ParameterizedTest
    @MethodSource("numFactories")
    void testSerializedValidBarRoundTrips(NumFactory numFactory) throws Exception {
        final BaseBar original = new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10),
                numFactory.numOf(20), numFactory.numOf(5), numFactory.numOf(15), numFactory.zero(), numFactory.zero(),
                0);
        final BaseBar restored = (BaseBar) deserialize(serialize(original));
        assertEquals(original.getOpenPrice(), restored.getOpenPrice());
        assertEquals(original.getHighPrice(), restored.getHighPrice());
        assertEquals(original.getLowPrice(), restored.getLowPrice());
        assertEquals(original.getClosePrice(), restored.getClosePrice());
        assertEquals(original.getBeginTime(), restored.getBeginTime());
        assertEquals(original.getEndTime(), restored.getEndTime());
    }

    @Test
    public void testSerializedBarWithInconsistentTimePeriodIsRejected() throws Exception {
        final BaseBar bar = new BaseBar(Duration.ofSeconds(123456789L), endTime.minusSeconds(123456789L), endTime,
                numFactory.numOf(10), numFactory.numOf(20), numFactory.numOf(5), numFactory.numOf(15),
                numFactory.zero(), numFactory.zero(), 0);
        final byte[] serialized = serialize(bar);
        final int periodOffset = indexOfLong(serialized, 123456789L);
        assertTrue(periodOffset >= 0, "serialized stream must contain the period");
        writeLong(serialized, periodOffset, 123456788L);
        assertThrows(InvalidObjectException.class, () -> deserialize(serialized));
    }

    @Test
    public void testSerializedBarViolatingOhlcInvariantIsRejected() throws Exception {
        // Use a DoubleNum-backed bar so the serialized stream contains raw double
        // fields that can be patched deterministically.
        final DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        final BaseBar bar = new BaseBar(Duration.ofHours(1), beginTime, endTime, doubleFactory.numOf(10),
                doubleFactory.numOf(987654.321d), doubleFactory.numOf(5), doubleFactory.numOf(15), doubleFactory.zero(),
                doubleFactory.zero(), 0);
        final byte[] serialized = serialize(bar);
        final int highOffset = indexOfDouble(serialized, 987654.321d);
        assertTrue(highOffset >= 0, "serialized stream must contain the high price");
        writeDouble(serialized, highOffset, 1.0d);
        assertThrows(InvalidObjectException.class, () -> deserialize(serialized));
    }

    @Test
    public void testSerializedBarWithNegativeVolumeIsRejected() throws Exception {
        final DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        final BaseBar bar = new BaseBar(Duration.ofHours(1), beginTime, endTime, doubleFactory.numOf(10),
                doubleFactory.numOf(20), doubleFactory.numOf(5), doubleFactory.numOf(15),
                doubleFactory.numOf(123456.789d), doubleFactory.zero(), 0);
        final byte[] serialized = serialize(bar);
        final int volumeOffset = indexOfDouble(serialized, 123456.789d);
        assertTrue(volumeOffset >= 0, "serialized stream must contain volume");
        writeDouble(serialized, volumeOffset, -1.0d);
        assertThrows(InvalidObjectException.class, () -> deserialize(serialized));
    }

    @Test
    public void testSerializedBarWithNegativeAmountIsRejected() throws Exception {
        final BaseBar bar = new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10),
                numFactory.numOf(20), numFactory.numOf(5), numFactory.numOf(15), numFactory.zero(),
                numFactory.numOf(123456.789d), 0);
        final byte[] serialized = serialize(bar);
        final int amountOffset = indexOfDouble(serialized, 123456.789d);
        assertTrue(amountOffset >= 0, "serialized stream must contain amount");
        writeDouble(serialized, amountOffset, -1.0d);
        assertThrows(InvalidObjectException.class, () -> deserialize(serialized));
    }

    @Test
    public void testSerializedBarWithNegativeTradesIsRejected() throws Exception {
        final DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        final long testTrades = 9876543210L;
        final BaseBar bar = new BaseBar(Duration.ofHours(1), beginTime, endTime, doubleFactory.numOf(10),
                doubleFactory.numOf(20), doubleFactory.numOf(5), doubleFactory.numOf(15), doubleFactory.zero(),
                doubleFactory.zero(), testTrades);
        final byte[] serialized = serialize(bar);
        final int tradesOffset = indexOfLong(serialized, testTrades);
        assertTrue(tradesOffset >= 0, "serialized stream must contain trades");
        writeLong(serialized, tradesOffset, -1L);
        assertThrows(InvalidObjectException.class, () -> deserialize(serialized));
    }

    private static byte[] serialize(Object value) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            return bytes.toByteArray();
        }
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    private static int indexOfDouble(byte[] bytes, double value) {
        final byte[] needle = doubleBytes(value);
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void writeDouble(byte[] bytes, int offset, double value) {
        System.arraycopy(doubleBytes(value), 0, bytes, offset, Double.BYTES);
    }

    private static byte[] doubleBytes(double value) {
        return ByteBuffer.allocate(Double.BYTES).putDouble(value).array();
    }

    private static int indexOfLong(byte[] bytes, long value) {
        final byte[] needle = ByteBuffer.allocate(Long.BYTES).putLong(value).array();
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        System.arraycopy(ByteBuffer.allocate(Long.BYTES).putLong(value).array(), 0, bytes, offset, Long.BYTES);
    }
}

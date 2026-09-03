/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import org.junit.Before;
import org.ta4j.core.num.DoubleNumFactory;
import org.junit.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BarTest extends AbstractIndicatorTest<BarSeries, Num> {

    private Bar bar;

    private Instant beginTime;

    private Instant endTime;

    public BarTest(final NumFactory numFactory) {
        super(null, numFactory);
    }

    @Before
    public void setUp() {
        this.beginTime = Instant.parse("2014-06-25T00:00:00Z");
        this.endTime = Instant.parse("2014-06-25T01:00:00Z");
        this.bar = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();
    }

    @Test
    public void testHighBelowLowRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), numFactory.numOf(8),
                        numFactory.numOf(9), numFactory.numOf(9), numFactory.zero(), numFactory.zero(), 0));
    }

    @Test
    public void testHighBelowOpenRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), numFactory.numOf(9.5),
                        numFactory.numOf(9), numFactory.numOf(9.4), numFactory.zero(), numFactory.zero(), 0));
    }

    @Test
    public void testHighBelowCloseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(9.4), numFactory.numOf(9.5),
                        numFactory.numOf(9), numFactory.numOf(10), numFactory.zero(), numFactory.zero(), 0));
    }

    @Test
    public void testLowAboveOpenRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(9), numFactory.numOf(10),
                        numFactory.numOf(9.5), numFactory.numOf(9.5), numFactory.zero(), numFactory.zero(), 0));
    }

    @Test
    public void testLowAboveCloseRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10.5),
                        numFactory.numOf(10.5), numFactory.numOf(9.5), numFactory.numOf(9), numFactory.zero(),
                        numFactory.zero(), 0));
    }

    @Test
    public void testNegativeVolumeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), numFactory.numOf(10),
                        numFactory.numOf(9), numFactory.numOf(9), numFactory.numOf(-1), numFactory.zero(), 0));
    }

    @Test
    public void testNegativeAmountRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), numFactory.numOf(10),
                        numFactory.numOf(9), numFactory.numOf(9), numFactory.zero(), numFactory.numOf(-1), 0));
    }

    @Test
    public void testNegativeTradesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), numFactory.numOf(10),
                        numFactory.numOf(9), numFactory.numOf(9), numFactory.zero(), numFactory.zero(), -1));
    }

    @Test
    public void testNullPricesTolerated() {
        final Bar nullPriceBar = new BaseBar(Duration.ofHours(1), beginTime, endTime, null, null, null, null, null,
                null, 0);
        assertNull(nullPriceBar.getOpenPrice());
    }

    @Test
    public void testAddPricePreservesOhlcInvariant() {
        // Partial bar with only an open price: extrema must fold in the existing
        // open instead of initializing from the added price alone.
        final BaseBar partialBar = new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), null,
                null, null, null, null, 0);
        partialBar.addPrice(numFactory.numOf(15));
        assertEquals(numFactory.numOf(10), partialBar.getOpenPrice());
        assertEquals(numFactory.numOf(15), partialBar.getClosePrice());
        assertEquals(numFactory.numOf(15), partialBar.getHighPrice());
        assertEquals(numFactory.numOf(10), partialBar.getLowPrice());
        partialBar.addPrice(numFactory.numOf(5));
        assertEquals(numFactory.numOf(5), partialBar.getClosePrice());
        assertEquals(numFactory.numOf(15), partialBar.getHighPrice());
        assertEquals(numFactory.numOf(5), partialBar.getLowPrice());
    }

    @Test
    public void testAddPriceFoldsPriorCloseIntoExtrema() {
        // Partial bar with open and close but no extrema: the added price must
        // not discard the existing close when initializing high and low.
        final BaseBar partialBar = new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), null,
                null, numFactory.numOf(20), null, null, 0);
        partialBar.addPrice(numFactory.numOf(15));
        assertEquals(numFactory.numOf(10), partialBar.getOpenPrice());
        assertEquals(numFactory.numOf(15), partialBar.getClosePrice());
        assertEquals(numFactory.numOf(20), partialBar.getHighPrice());
        assertEquals(numFactory.numOf(10), partialBar.getLowPrice());

        // Symmetric low-side fold when the prior close is below the open.
        final BaseBar lowBar = new BaseBar(Duration.ofHours(1), beginTime, endTime, numFactory.numOf(10), null, null,
                numFactory.numOf(5), null, null, 0);
        lowBar.addPrice(numFactory.numOf(12));
        assertEquals(numFactory.numOf(10), lowBar.getOpenPrice());
        assertEquals(numFactory.numOf(12), lowBar.getClosePrice());
        assertEquals(numFactory.numOf(12), lowBar.getHighPrice());
        assertEquals(numFactory.numOf(5), lowBar.getLowPrice());
    }

    @Test
    public void testSerializedValidBarRoundTrips() throws Exception {
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
    public void testSerializedBarViolatingOhlcInvariantIsRejected() throws Exception {
        // Use a DoubleNum-backed bar so the serialized stream contains raw double
        // fields that can be patched deterministically.
        final DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        final BaseBar bar = new BaseBar(Duration.ofHours(1), beginTime, endTime, doubleFactory.numOf(10),
                doubleFactory.numOf(987654.321d), doubleFactory.numOf(5), doubleFactory.numOf(15), doubleFactory.zero(),
                doubleFactory.zero(), 0);
        final byte[] serialized = serialize(bar);
        final int highOffset = indexOfDouble(serialized, 987654.321d);
        assertTrue("serialized stream must contain the high price", highOffset >= 0);
        writeDouble(serialized, highOffset, 1.0d);
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

    @Test
    public void createBars() {
        var barByBeginTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .beginTime(this.beginTime)
                .volume(0)
                .amount(0)
                .build();

        var barByEndTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();

        var barByBeginTimeAndEndTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .beginTime(this.beginTime)
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();

        var barWithoutTimePeriod = new TimeBarBuilder(this.numFactory).beginTime(this.beginTime)
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();

        assertEquals(barByBeginTime.getBeginTime(), barByEndTime.getBeginTime());
        assertEquals(barByBeginTime.getEndTime(), barByEndTime.getEndTime());
        assertEquals(barByBeginTimeAndEndTime.getTimePeriod(), barWithoutTimePeriod.getTimePeriod());
        assertEquals(barByBeginTimeAndEndTime.getTimePeriod(), Duration.between(beginTime, endTime));
        assertNotEquals(barByBeginTimeAndEndTime.getTimePeriod(), Duration.between(endTime, beginTime));
        assertEquals(barWithoutTimePeriod.getTimePeriod(), Duration.between(beginTime, endTime));
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("unused")
    public void createBarsWithMissingBeginTime() {
        // TimePeriod is not given and cannot be computed due to missing beginTime.
        var bar = new TimeBarBuilder(this.numFactory).endTime(endTime).volume(0).amount(0).build();
    }

    @Test(expected = NullPointerException.class)
    @SuppressWarnings("unused")
    public void createBarsWithMissingEndTime() {
        // TimePeriod is not given and cannot be computed due to missing endTime.
        var bar = new TimeBarBuilder(this.numFactory).beginTime(beginTime).volume(0).amount(0).build();
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void createBarsWithInvalidTimePeriod() {
        var barByBeginTime = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(2))
                .beginTime(this.beginTime)
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();
    }

    @Test
    public void addTrades() {

        this.bar.addTrade(numOf(3.0), numOf(200.0));
        this.bar.addTrade(numOf(4.0), numOf(201.0));
        this.bar.addTrade(numOf(2.0), numOf(198.0));

        assertEquals(3, this.bar.getTrades());
        assertEquals(numOf(3 * 200 + 4 * 201 + 2 * 198), this.bar.getAmount());
        assertEquals(numOf(200), this.bar.getOpenPrice());
        assertEquals(numOf(198), this.bar.getClosePrice());
        assertEquals(numOf(198), this.bar.getLowPrice());
        assertEquals(numOf(201), this.bar.getHighPrice());
        assertEquals(numOf(9), this.bar.getVolume());
    }

    @Test
    public void getTimePeriod() {
        assertEquals(this.beginTime, this.bar.getEndTime().minus(this.bar.getTimePeriod()));
    }

    @Test
    public void getBeginTime() {
        assertEquals(this.beginTime, this.bar.getBeginTime());
    }

    @Test
    public void getDateName() {
        assertNotNull(bar.getDateName());
    }

    @Test
    public void getSimpleDateName() {
        assertNotNull(bar.getSimpleDateName());
    }

    @Test
    public void inPeriod() {
        assertFalse(this.bar.inPeriod(null));

        ZonedDateTime zonedBeginTime = beginTime.atZone(ZoneOffset.UTC);
        assertFalse(bar.inPeriod(zonedBeginTime.withDayOfMonth(24).toInstant()));
        assertFalse(bar.inPeriod(zonedBeginTime.withDayOfMonth(26).toInstant()));
        assertTrue(bar.inPeriod(zonedBeginTime.withMinute(30).toInstant()));

        assertTrue(this.bar.inPeriod(this.beginTime));
        assertFalse(this.bar.inPeriod(this.endTime));
    }

    @Test
    public void doesNotThrowNullPointerException() {
        var bar = new TimeBarBuilder().timePeriod(Duration.ofHours(1)).endTime(endTime).build();
        // TODO use Junit5: org.junit.jupiter.api.Assertions.assertDoesNotThrow instead:
        assertNotNull(bar.toString());
    }

    @Test
    public void equals() {
        final Bar bar1 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();
        final Bar bar2 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();

        assertEquals(bar1, bar2);
        assertNotSame(bar1, bar2);
    }

    @Test
    public void hashCode2() {
        final Bar bar1 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();
        final Bar bar2 = new TimeBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();

        assertEquals(bar1.hashCode(), bar2.hashCode());
    }

    @Test
    public void numFactoryPrefersOpenPrice() {
        var bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofSeconds(1))
                .beginTime(Instant.now())
                .openPrice(1)
                .closePrice(2)
                .build();

        assertSame(bar.getOpenPrice().getClass(), bar.numFactory().one().getClass());
    }

    @Test
    public void numFactoryFallsBackToClosePrice() {
        var bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofSeconds(1))
                .beginTime(Instant.now())
                .closePrice(2)
                .build();

        assertSame(bar.getClosePrice().getClass(), bar.numFactory().one().getClass());
    }

    @Test
    public void numFactoryThrowsWhenNoPricesAvailable() {
        var bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofSeconds(1)).beginTime(Instant.now()).build();

        assertThrows(IllegalArgumentException.class, bar::numFactory);
    }
}

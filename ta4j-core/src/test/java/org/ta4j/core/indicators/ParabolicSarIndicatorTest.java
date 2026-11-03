/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ParabolicSarIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ParabolicSarIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void growingBarSeriesTest() {
        var now = Instant.now();
        var mockBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        mockBarSeries.barBuilder()
                .endTime(now)
                .openPrice(74.5)
                .closePrice(75.1)
                .highPrice(75.11)
                .lowPrice(74.06)
                .build();

        mockBarSeries.setMaximumBarCount(3);

        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(1))
                .openPrice(75.09)
                .closePrice(75.9)
                .highPrice(76.030000)
                .lowPrice(74.640000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(2))
                .openPrice(75.060000)
                .closePrice(75.24)
                .highPrice(76.269900)
                .lowPrice(75.060000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(3))
                .openPrice(74.500000)
                .closePrice(75.17)
                .highPrice(75.280000)
                .lowPrice(74.500000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(4))
                .openPrice(75.16)
                .closePrice(74.6)
                .highPrice(75.310000)
                .lowPrice(74.540000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(5))
                .openPrice(74.58)
                .closePrice(74.1)
                .highPrice(75.467000)
                .lowPrice(74.010000)
                .add();

        var sar = new ParabolicSarIndicator(mockBarSeries);

        assertEquals(1, sar.getCountOfUnstableBars());
        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getBeginIndex()));
        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount()));

        assertNotEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount() + 1));
        assertNotEquals(NaN.NaN, sar.getValue(mockBarSeries.getEndIndex()));
    }

    @Test
    public void startUpAndDownTrendTest() {
        var now = Instant.now();
        var mockBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // values of removable bars are much higher to be sure that they will not affect
        // the result.
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(1))
                .openPrice(170.1)
                .closePrice(175.1)
                .highPrice(180.10)
                .lowPrice(170.1)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(2))
                .openPrice(180.2)
                .closePrice(185.1)
                .highPrice(190.20)
                .lowPrice(180.2)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(3))
                .openPrice(190.3)
                .closePrice(195.1)
                .highPrice(200.30)
                .lowPrice(190.3)
                .add();

        mockBarSeries.setMaximumBarCount(21);

        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(4))
                .openPrice(74.5)
                .closePrice(75.1)
                .highPrice(75.11)
                .lowPrice(74.06)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(5))
                .openPrice(75.09)
                .closePrice(75.9)
                .highPrice(76.030000)
                .lowPrice(74.640000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(6))
                .openPrice(75.060000)
                .closePrice(75.24)
                .highPrice(76.269900)
                .lowPrice(75.060000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(7))
                .openPrice(74.500000)
                .closePrice(75.17)
                .highPrice(75.280000)
                .lowPrice(74.500000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(8))
                .openPrice(75.16)
                .closePrice(74.6)
                .highPrice(75.310000)
                .lowPrice(74.540000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(9))
                .openPrice(74.58)
                .closePrice(74.1)
                .highPrice(75.467000)
                .lowPrice(74.010000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(10))
                .openPrice(74.01)
                .closePrice(73.740000)
                .highPrice(74.700000)
                .lowPrice(73.546000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(11))
                .openPrice(73.71)
                .closePrice(73.390000)
                .highPrice(73.830000)
                .lowPrice(72.720000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(12))
                .openPrice(73.35)
                .closePrice(73.25)
                .highPrice(73.890000)
                .lowPrice(72.86)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(13))
                .openPrice(73.24)
                .closePrice(74.36)
                .highPrice(74.410000)
                .lowPrice(73)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(14))
                .openPrice(74.820000)
                .closePrice(76.510000)
                .highPrice(76.830000)
                .lowPrice(74.820000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(15))
                .openPrice(76.5)
                .closePrice(75.590000)
                .highPrice(76.850000)
                .lowPrice(74.540000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(16))
                .openPrice(75.60)
                .closePrice(75.910000)
                .highPrice(76.960000)
                .lowPrice(75.510000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(17))
                .openPrice(75.82)
                .closePrice(74.610000)
                .highPrice(77.070000)
                .lowPrice(74.560000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(18))
                .openPrice(74.75)
                .closePrice(75.330000)
                .highPrice(75.530000)
                .lowPrice(74.010000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(19))
                .openPrice(75.33)
                .closePrice(75.010000)
                .highPrice(75.500000)
                .lowPrice(74.510000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(20))
                .openPrice(75.250000)
                .closePrice(75.620000)
                .highPrice(76.210000)
                .lowPrice(75.250000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(21))
                .openPrice(75.63)
                .closePrice(76.040000)
                .highPrice(76.460000)
                .lowPrice(75.092800)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(22))
                .openPrice(76.0)
                .closePrice(76.450000)
                .highPrice(76.450000)
                .lowPrice(75.435000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(23))
                .openPrice(76.45)
                .closePrice(76.260000)
                .highPrice(76.470000)
                .lowPrice(75.840000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(24))
                .openPrice(76.30)
                .closePrice(76.850000)
                .highPrice(77.000000)
                .lowPrice(76.190000)
                .add();

        var sar = new ParabolicSarIndicator(mockBarSeries);

        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount())); // first bar in series
        assertNumEquals(74.06, sar.getValue(mockBarSeries.getRemovedBarsCount() + 1));
        assertNumEquals(74.06, sar.getValue(mockBarSeries.getRemovedBarsCount() + 2)); // start with up trend
        assertNumEquals(74.148396, sar.getValue(mockBarSeries.getRemovedBarsCount() + 3)); // switch to downtrend
        assertNumEquals(74.23325616000001, sar.getValue(mockBarSeries.getRemovedBarsCount() + 4)); // hold trend...
        assertNumEquals(76.2699, sar.getValue(mockBarSeries.getRemovedBarsCount() + 5));
        assertNumEquals(76.22470200000001, sar.getValue(mockBarSeries.getRemovedBarsCount() + 6));
        assertNumEquals(76.11755392, sar.getValue(mockBarSeries.getRemovedBarsCount() + 7));
        assertNumEquals(75.9137006848, sar.getValue(mockBarSeries.getRemovedBarsCount() + 8));
        assertNumEquals(75.72207864371201, sar.getValue(mockBarSeries.getRemovedBarsCount() + 9)); // switch to up trend
        assertNumEquals(72.72, sar.getValue(mockBarSeries.getRemovedBarsCount() + 10));// hold trend
        assertNumEquals(72.8022, sar.getValue(mockBarSeries.getRemovedBarsCount() + 11)); // ??? trend changed?
        assertNumEquals(72.964112, sar.getValue(mockBarSeries.getRemovedBarsCount() + 12));
        assertNumEquals(73.20386528, sar.getValue(mockBarSeries.getRemovedBarsCount() + 13));
        assertNumEquals(73.5131560576, sar.getValue(mockBarSeries.getRemovedBarsCount() + 14));
        assertNumEquals(73.797703572992, sar.getValue(mockBarSeries.getRemovedBarsCount() + 15));
        assertNumEquals(74.01, sar.getValue(mockBarSeries.getRemovedBarsCount() + 16));
        assertNumEquals(74.2548, sar.getValue(mockBarSeries.getRemovedBarsCount() + 17));
        assertNumEquals(74.480016, sar.getValue(mockBarSeries.getRemovedBarsCount() + 18));
        assertNumEquals(74.68721472, sar.getValue(mockBarSeries.getRemovedBarsCount() + 19));
        assertNumEquals(74.8778375424, sar.getValue(mockBarSeries.getRemovedBarsCount() + 20));
    }

    @Test
    public void startWithDownAndUpTrendTest() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        // The first daily candle of BTCUSDT in the Binance cryptocurrency exchange. 17
        // Aug 2017..
        series.barBuilder().openPrice(4261.48).closePrice(4285.08).highPrice(4485.39).lowPrice(4200.74).add();
        // starting with down trend..
        series.barBuilder().openPrice(4285.08).closePrice(4108.37).highPrice(4371.52).lowPrice(3938.77).add();
        // hold trend...
        series.barBuilder().openPrice(4108.37).closePrice(4139.98).highPrice(4184.69).lowPrice(3850.00).add();
        series.barBuilder().openPrice(4120.98).closePrice(4086.29).highPrice(4211.08).lowPrice(4032.62).add();
        series.barBuilder().openPrice(4069.13).closePrice(4016.00).highPrice(4119.62).lowPrice(3911.79).add();
        series.barBuilder().openPrice(4016.00).closePrice(4040.00).highPrice(4104.82).lowPrice(3400.00).add();
        series.barBuilder().openPrice(4040.00).closePrice(4114.01).highPrice(4265.80).lowPrice(4013.89).add();
        // switch to up trend
        series.barBuilder().openPrice(4147.00).closePrice(4316.01).highPrice(4371.68).lowPrice(4085.01).add();
        // hold trend
        series.barBuilder().openPrice(4316.01).closePrice(4280.68).highPrice(4453.91).lowPrice(4247.48).add();
        series.barBuilder().openPrice(4280.71).closePrice(4337.44).highPrice(4367.00).lowPrice(4212.41).add();

        var sar = new ParabolicSarIndicator(series);

        assertEquals(NaN.NaN, sar.getValue(0));
        assertNumEquals(4485.39000000, sar.getValue(1));
        assertNumEquals(4485.39000000, sar.getValue(2));
        assertNumEquals(4459.97440000, sar.getValue(3));
        assertNumEquals(4435.57542400, sar.getValue(4));
        assertNumEquals(4412.15240704, sar.getValue(5));
        assertNumEquals(4351.42326262, sar.getValue(6));
        assertNumEquals(3400.00000000, sar.getValue(7));
        assertNumEquals(3419.43360000, sar.getValue(8));
        assertNumEquals(3460.81265600, sar.getValue(9));
    }

    @Test
    public void testSameValueForSameIndex() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(4261.48).closePrice(4285.08).highPrice(4485.39).lowPrice(4200.74).add();
        series.barBuilder().openPrice(4285.08).closePrice(4108.37).highPrice(4371.52).lowPrice(3938.77).add();
        series.barBuilder().openPrice(4108.37).closePrice(4139.98).highPrice(4184.69).lowPrice(3850.00).add();
        series.barBuilder().openPrice(4120.98).closePrice(4086.29).highPrice(4211.08).lowPrice(4032.62).add();
        series.barBuilder().openPrice(4069.13).closePrice(4016.00).highPrice(4119.62).lowPrice(3911.79).add();
        series.barBuilder().openPrice(4016.00).closePrice(4040.00).highPrice(4104.82).lowPrice(3400.00).add();
        series.barBuilder().openPrice(4040.00).closePrice(4114.01).highPrice(4265.80).lowPrice(4013.89).add();
        series.barBuilder().openPrice(4147.00).closePrice(4316.01).highPrice(4371.68).lowPrice(4085.01).add();
        series.barBuilder().openPrice(4316.01).closePrice(4280.68).highPrice(4453.91).lowPrice(4247.48).add();
        series.barBuilder().openPrice(4280.71).closePrice(4337.44).highPrice(4367.00).lowPrice(4212.41).add();

        final ParabolicSarIndicator parabolicSarIndicator = new ParabolicSarIndicator(series);

        final List<Num> values = IntStream.range(0, series.getBarCount())
                .mapToObj(parabolicSarIndicator::getValue)
                .collect(Collectors.toList());

        assertNumEquals(values.get(0), parabolicSarIndicator.getValue(0));
        assertNumEquals(values.get(5), parabolicSarIndicator.getValue(5));
        assertNumEquals(values.get(1), parabolicSarIndicator.getValue(1));
        assertNumEquals(values.get(4), parabolicSarIndicator.getValue(4));
        assertNumEquals(values.get(2), parabolicSarIndicator.getValue(2));
        assertNumEquals(values.get(3), parabolicSarIndicator.getValue(3));
        assertNumEquals(values.get(1), parabolicSarIndicator.getValue(1));
        assertNumEquals(values.get(9), parabolicSarIndicator.getValue(9));
        assertNumEquals(values.get(9), parabolicSarIndicator.getValue(9));
        assertNumEquals(values.get(8), parabolicSarIndicator.getValue(8));
        assertNumEquals(values.get(7), parabolicSarIndicator.getValue(7));
        assertNumEquals(values.get(6), parabolicSarIndicator.getValue(6));
    }

    @Test
    public void movingSeriesContinuesWholeSarState() {
        for (int capacity : new int[] { 20, 60, 200 }) {
            BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            moving.setMaximumBarCount(capacity);
            BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            ParabolicSarIndicator reused = new ParabolicSarIndicator(moving);
            ParabolicSarIndicator reference = new ParabolicSarIndicator(unbounded);
            for (int i = 0; i < 600; i++) {
                appendTrendBar(moving, i);
                appendTrendBar(unbounded, i);
                assertNumEquals(reference.getValue(i), reused.getValue(i));
            }
            ParabolicSarIndicator fresh = new ParabolicSarIndicator(moving);
            BarSeries retained = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            for (int i = moving.getBeginIndex(); i <= moving.getEndIndex(); i++) {
                retained.addBar(moving.getBar(i));
            }
            ParabolicSarIndicator retainedReference = new ParabolicSarIndicator(retained);
            for (int i = moving.getBeginIndex(); i <= moving.getEndIndex(); i++) {
                assertNumEquals(retainedReference.getValue(i - moving.getBeginIndex()), fresh.getValue(i));
                assertNumEquals(reference.getValue(i), reused.getValue(i));
            }
            // The head seed is NaN even when the continued chain has a trend.
            assertEquals(NaN.NaN, fresh.getValue(moving.getBeginIndex()));
            assertNotEquals(NaN.NaN, reused.getValue(moving.getBeginIndex()));
        }
    }

    @Test
    public void terminalEditReadAndAppendUsesCorrectedTuple() {
        for (boolean readAfterEdit : new boolean[] { false, true }) {
            BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            moving.setMaximumBarCount(20);
            BarSeries history = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            ParabolicSarIndicator reused = new ParabolicSarIndicator(moving);
            for (int i = 0; i < 45; i++) {
                appendTrendBar(moving, i);
                appendTrendBar(history, i);
                reused.getValue(i);
            }
            moving.getLastBar().addPrice(numOf(140));
            history.getLastBar().addPrice(numOf(140));
            ParabolicSarIndicator corrected = new ParabolicSarIndicator(history);
            if (readAfterEdit) {
                assertNumEquals(corrected.getValue(44), reused.getValue(44));
            }
            for (int i = 45; i < 51; i++) {
                appendTrendBar(moving, i);
                appendTrendBar(history, i);
                assertNumEquals(corrected.getValue(i), reused.getValue(i));
            }
        }
    }

    @Test
    public void historicalReplacementInvalidatesWholeTupleSuffix() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 35; i++) {
            appendTrendBar(series, i);
        }
        ParabolicSarIndicator reused = new ParabolicSarIndicator(series);
        reused.getValue(34);
        Bar old = series.getBar(15);
        Bar replacement = series.barBuilder()
                .endTime(old.getEndTime())
                .timePeriod(old.getTimePeriod())
                .openPrice(120)
                .highPrice(130)
                .lowPrice(90)
                .closePrice(125)
                .build();
        series.replaceBar(15, replacement);
        ParabolicSarIndicator corrected = new ParabolicSarIndicator(series);
        for (int i = 15; i < 35; i++) {
            assertNumEquals(corrected.getValue(i), reused.getValue(i));
        }
        appendTrendBar(series, 35);
        assertNumEquals(corrected.getValue(35), reused.getValue(35));
    }

    @Test
    public void missedHistoryReseedsAndLongGapsPrefillIteratively() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(200);
        ParabolicSarIndicator reused = new ParabolicSarIndicator(series);
        for (int i = 0; i < 450; i++) {
            appendTrendBar(series, i);
            if (i < 10) {
                reused.getValue(i);
            }
        }
        ParabolicSarIndicator fresh = new ParabolicSarIndicator(series);
        assertNumEquals(fresh.getValue(449), reused.getValue(449));
        for (int i : new int[] { 300, 250, 448, 400, 449 }) {
            assertNumEquals(fresh.getValue(i), reused.getValue(i));
        }
        appendTrendBar(series, 450);
        assertNumEquals(fresh.getValue(450), reused.getValue(450));
    }

    @Test
    public void normalAppendCalculatesOneBoundedState() {
        for (int capacity : new int[] { 20, 60, 200 }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            series.setMaximumBarCount(capacity);
            AtomicInteger calculations = new AtomicInteger();
            ParabolicSarIndicator sar = new ParabolicSarIndicator(series, numOf(0.02), numOf(0.2), numOf(0.02),
                    index -> calculations.incrementAndGet());
            CachedIndicator<?> state = (CachedIndicator<?>) sar.getDependencies().getFirst();
            for (int i = 0; i < 2 * capacity + 30; i++) {
                appendTrendBar(series, i);
                int before = calculations.get();
                sar.getValue(i);
                assertEquals(1, calculations.get() - before);
                sar.getValue(i);
                assertEquals(1, calculations.get() - before);
                assertTrue(state.getCache().getCapacity() <= capacity);
                if (i > capacity) {
                    assertTrue(state.getCache().getFirstCachedIndex() >= series.getBeginIndex());
                    assertTrue(state.getCache().getHighestResultIndex() - state.getCache().getFirstCachedIndex()
                            + 1 <= capacity);
                }
            }
        }
    }

    @Test
    public void headAdvanceDuringStateReadRetriesAgainstNewHead() throws Exception {
        BarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .withMaxBarCount(5)
                .build();
        for (int i = 0; i <= 10; i++) {
            appendTrendBar(series, i);
        }
        AtomicBoolean triggered = new AtomicBoolean();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        BarSeries advancing = interceptBarReads(series, index -> {
            if (index == 8 && triggered.compareAndSet(false, true)) {
                reading.countDown();
                try {
                    assertTrue(resume.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
        });
        ParabolicSarIndicator raced = new ParabolicSarIndicator(advancing);
        Num actual;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> result = executor.submit(() -> raced.getValue(10));
            assertTrue(reading.await(10, TimeUnit.SECONDS));
            for (int i = 11; i <= 13; i++) {
                appendTrendBar(series, i);
            }
            resume.countDown();
            actual = result.get(10, TimeUnit.SECONDS);
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
        ParabolicSarIndicator fresh = new ParabolicSarIndicator(series);
        assertTrue(triggered.get());
        assertEquals(9, series.getBeginIndex());
        assertNumEquals(fresh.getValue(10), actual);
        for (int i = 9; i <= 13; i++) {
            assertNumEquals(fresh.getValue(i), raced.getValue(i));
        }
    }

    @Test
    public void boundedSerializationReconstructsFreshRetainedHistory() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(20);
        ParabolicSarIndicator reused = new ParabolicSarIndicator(series, numOf(0.01), numOf(0.15), numOf(0.005));
        for (int i = 0; i < 45; i++) {
            appendTrendBar(series, i);
            reused.getValue(i);
        }
        ParabolicSarIndicator descriptorCopy = (ParabolicSarIndicator) IndicatorSerialization.fromDescriptor(series,
                reused.toDescriptor());
        ParabolicSarIndicator jsonCopy = (ParabolicSarIndicator) Indicator.fromJson(series, reused.toJson());
        ParabolicSarIndicator fresh = new ParabolicSarIndicator(series, numOf(0.01), numOf(0.15), numOf(0.005));
        assertEquals(reused.toDescriptor(), descriptorCopy.toDescriptor());
        assertEquals(reused.toDescriptor(), jsonCopy.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(fresh.getValue(i), descriptorCopy.getValue(i));
            assertNumEquals(fresh.getValue(i), jsonCopy.getValue(i));
        }
    }

    private BarSeries interceptBarReads(BarSeries delegate, IntConsumer beforeRead) {
        return (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(), new Class<?>[] { BarSeries.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getBar")) {
                        beforeRead.accept((Integer) args[0]);
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private void appendTrendBar(BarSeries series, int index) {
        double close = 100 + (index / 80 % 2 == 0 ? index % 80 : 80 - index % 80) / 8;
        series.barBuilder()
                .endTime(Instant.EPOCH.plusSeconds(index + 1))
                .openPrice(close - 0.25)
                .highPrice(close + 1)
                .lowPrice(close - 1)
                .closePrice(close)
                .add();
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series,
                new ParabolicSarIndicator(series, numOf(0.02), numOf(0.2), numOf(0.02)), stableIndexes(series)));
    }

}

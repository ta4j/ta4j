/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    double[] typicalPrices = { 23.98, 23.92, 23.79, 23.67, 23.54, 23.36, 23.65, 23.72, 24.16, 23.91, 23.81, 23.92,
            23.74, 24.68, 24.94, 24.93, 25.10, 25.12, 25.20, 25.06, 24.50, 24.31, 24.57, 24.62, 24.49, 24.37, 24.41,
            24.35, 23.75, 24.09 };
    BarSeries data;

    public IndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(typicalPrices).build();
    }

    @Test
    public void toDouble() {
        List<Num> expectedValues = Arrays.stream(typicalPrices)
                .mapToObj(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator closePriceMockIndicator = new MockIndicator(data, expectedValues);

        int barCount = 10, index = 20;
        Double[] doubles = Indicator.toDouble(closePriceMockIndicator, index, barCount);
        assertTrue(doubles.length == barCount);

        for (int i = 0; i < barCount; i++) {
            assertTrue(typicalPrices[i + 11] == doubles[i]);
        }
    }

    @Test
    public void shouldProvideStream() {
        List<Num> expectedValues = Arrays.stream(typicalPrices)
                .mapToObj(numFactory::numOf)
                .collect(Collectors.toList());
        MockIndicator closePriceMockIndicator = new MockIndicator(data, expectedValues);

        Stream<Num> stream = closePriceMockIndicator.stream();
        List<Num> collectedValues = stream.collect(Collectors.toList());

        Assertions.assertNotNull(stream);
        Assertions.assertNotNull(collectedValues);
        assertEquals(30, collectedValues.size());
        for (int i = 0; i < data.getBarCount(); i++) {
            assertNumEquals(typicalPrices[i], collectedValues.get(i));
        }
    }

    @Test
    public void streamMatchesLogicalWindowForConstrainedAndPrunedSeries() {
        List<BarSeries> scenarios = List.of(
                ConstrainedSeriesSupport.offsetSeries("constrained-stream", numFactory, 2, 5, 0, 10, 20, 30, 40, 50,
                        60),
                ConstrainedSeriesSupport.offsetSeries("pruned-stream", numFactory, 2, 5, 2, 10, 20, 30, 40, 50, 60),
                ConstrainedSeriesSupport.offsetSeries("constrained-pruned-stream", numFactory, 3, 5, 2, 10, 20, 30, 40,
                        50, 60));

        for (BarSeries scenario : scenarios) {
            assertStreamEqualsFreshLogicalSeries(scenario);
        }
    }

    @Test
    public void streamHandlesEmptySingleBarAndTerminalIndexWindows() {
        BarSeries empty = ConstrainedSeriesSupport.emptyLogicalSeries("empty-stream", numFactory, 10, 20);
        BarSeries single = ConstrainedSeriesSupport.offsetSeries("single-stream", numFactory, 4, 4, 3, 10, 20, 30, 40,
                50, 60);

        assertStreamEqualsFreshLogicalSeries(empty);
        assertStreamEqualsFreshLogicalSeries(single);

        BarSeries terminal = ConstrainedSeriesSupport.terminalOneBarSeries("terminal-stream", numFactory, 42);
        assertEquals(List.of(numFactory.numOf(42)),
                closePriceIndicator(terminal).stream().collect(Collectors.toList()));
    }

    private void assertStreamEqualsFreshLogicalSeries(BarSeries series) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        int logicalBarCount = beginIndex < 0 || endIndex < beginIndex ? 0 : endIndex - beginIndex + 1;
        double[] logicalCloses = new double[logicalBarCount];
        for (int offset = 0; offset < logicalCloses.length; offset++) {
            logicalCloses[offset] = series.getBar(beginIndex + offset).getClosePrice().doubleValue();
        }
        BarSeries equivalent = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(logicalCloses).build();
        assertEquals(closePriceIndicator(equivalent).stream().collect(Collectors.toList()),
                closePriceIndicator(series).stream().collect(Collectors.toList()));
    }

    private Indicator<Num> closePriceIndicator(BarSeries series) {
        return new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return series.getBar(index).getClosePrice();
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }

    @Test
    public void indicatorSeriesViewReadScopesHoldDelegateLock() throws Exception {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(42).build();
        ReentrantReadWriteLock seriesLock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, seriesLock);
        BarSeries indicatorSeries = new ClosePriceIndicator(series).getBarSeries();
        BarSeriesChangeSnapshot before = series.getBarSeriesChangeSnapshot(-1L);
        CountDownLatch writerStarted = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?>[] writer = new Future<?>[1];

        try {
            int count = indicatorSeries.withReadLock(() -> {
                writer[0] = executorService.submit(() -> {
                    writerStarted.countDown();
                    series.addPrice(numFactory.numOf(43));
                });
                try {
                    assertTrue(writerStarted.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("writer did not start", interruption);
                }

                boolean writeLockAcquired = seriesLock.writeLock().tryLock();
                if (writeLockAcquired) {
                    seriesLock.writeLock().unlock();
                }
                assertFalse(writeLockAcquired, "indicator read scope must hold the delegate lock");
                assertEquals(before, indicatorSeries.getBarSeriesChangeSnapshot(-1L));
                assertNumEquals(numFactory.numOf(42), indicatorSeries.getBar(0).getClosePrice());
                return indicatorSeries.getBarCount();
            });
            assertEquals(1, count);

            writer[0].get(2, TimeUnit.SECONDS);
            assertTrue(series.getBarSeriesChangeSnapshot(-1L).revision() > before.revision());
            assertNumEquals(numFactory.numOf(43), indicatorSeries.getBar(0).getClosePrice());

            indicatorSeries.withReadLock((Runnable) () -> {
                boolean writeLockAcquired = seriesLock.writeLock().tryLock();
                if (writeLockAcquired) {
                    seriesLock.writeLock().unlock();
                }
                assertFalse(writeLockAcquired, "indicator runnable scope must hold the delegate lock");
            });
        } finally {
            executorService.shutdownNow();
        }
    }
}

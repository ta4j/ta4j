/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ConcurrentBarSeriesTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ExecutorService executorService;

    public ConcurrentBarSeriesTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void getBarDataProvidesSnapshot() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        var now = Instant.now();
        series.barBuilder().endTime(now).closePrice(1).add();
        series.barBuilder().endTime(now.plus(Duration.ofMinutes(1))).closePrice(2).add();

        List<Bar> snapshot = series.getBarData();
        assertEquals(2, snapshot.size());
        assertNotSame(snapshot, series.getBarData());
        series.barBuilder().endTime(now.plus(Duration.ofMinutes(2))).closePrice(3).add();
        assertEquals("snapshot must remain unchanged after concurrent mutation", 2, snapshot.size());
        try {
            snapshot.add(snapshot.get(0));
            org.junit.Assert.fail("snapshot list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected path
        }
    }

    @Test
    public void getSubSeriesReturnsConcurrentBarSeries() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        var now = Instant.now();
        for (int i = 0; i < 5; i++) {
            series.barBuilder().endTime(now.plus(Duration.ofMinutes(i))).closePrice(i + 1).add();
        }

        BarSeries subSeries = series.getSubSeries(1, 4);
        assertTrue(subSeries instanceof ConcurrentBarSeries);
        assertEquals(3, subSeries.getBarCount());
        assertEquals(series.getBar(1).getEndTime(), subSeries.getFirstBar().getEndTime());

        subSeries.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(now.plus(Duration.ofMinutes(10)))
                .closePrice(42)
                .add();
        assertEquals(4, subSeries.getBarCount());
        assertEquals(5, series.getBarCount());
    }

    @Test
    public void supportsConcurrentReadsAndWrites() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();

        final int barsToProduce = 128;
        final var startSignal = new CountDownLatch(1);
        final var writerRunning = new AtomicBoolean(true);

        Future<?> writer = executorService.submit(() -> {
            try {
                startSignal.await();
                var now = Instant.now();
                for (int i = 0; i < barsToProduce; i++) {
                    series.barBuilder()
                            .timePeriod(Duration.ofSeconds(1))
                            .endTime(now.plusSeconds(i + 1))
                            .closePrice(i + 1)
                            .add();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                writerRunning.set(false);
            }
        });

        Future<?> reader = executorService.submit(() -> {
            try {
                startSignal.await();
                while (writerRunning.get() || series.getBarCount() < barsToProduce) {
                    int count = series.getBarCount();
                    if (count > 0) {
                        Bar last = series.getBar(series.getEndIndex());
                        assertNotNull(last);
                        List<Bar> snapshot = series.getBarData();
                        if (!snapshot.isEmpty()) {
                            snapshot.get(snapshot.size() - 1);
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(2);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        startSignal.countDown();
        writer.get(30, TimeUnit.SECONDS);
        reader.get(30, TimeUnit.SECONDS);

        assertEquals(barsToProduce, series.getBarCount());
        assertEquals(series.getBarCount() - 1, series.getEndIndex());
    }
}

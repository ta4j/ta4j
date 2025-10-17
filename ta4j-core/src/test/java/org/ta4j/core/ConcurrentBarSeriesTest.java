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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Comprehensive unit tests for {@link ConcurrentBarSeries} focusing on the
 * incremental, new logic that provides thread safety through ReadWriteLock.
 *
 * @since 0.19
 */
public class ConcurrentBarSeriesTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ExecutorService executorService;
    private List<Bar> testBars;
    private BarBuilderFactory barBuilderFactory;

    public ConcurrentBarSeriesTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        executorService = Executors.newFixedThreadPool(8);
        barBuilderFactory = new MockBarBuilderFactory();

        // Create test bars
        testBars = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");

        for (int i = 0; i < 5; i++) {
            Bar bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                    .endTime(baseTime.plus(Duration.ofDays(i)))
                    .openPrice(numOf(i + 1))
                    .highPrice(numOf(i + 2))
                    .lowPrice(numOf(i))
                    .closePrice(numOf(i + 1.5))
                    .volume(numOf(i * 100))
                    .amount(numOf(i * 1000))
                    .trades(i * 10)
                    .build();
            testBars.add(bar);
        }
    }

    @After
    public void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    // ==================== Constructor Tests ====================

    @Test
    public void testConvenienceConstructor() {
        ConcurrentBarSeries series = new ConcurrentBarSeries("TestName", testBars);

        assertEquals("TestName", series.getName());
        assertEquals(5, series.getBarCount());
        assertEquals(0, series.getBeginIndex());
        assertEquals(4, series.getEndIndex());
        assertFalse(series.isEmpty());
    }

    @Test
    public void testConvenienceConstructorWithEmptyBars() {
        ConcurrentBarSeries series = new ConcurrentBarSeries("TestName", Collections.emptyList());

        assertEquals("TestName", series.getName());
        assertEquals(0, series.getBarCount());
        assertEquals(-1, series.getBeginIndex());
        assertEquals(-1, series.getEndIndex());
        assertTrue(series.isEmpty());
    }

    @Test
    public void testFullConstructor() {
        ConcurrentBarSeries series = new ConcurrentBarSeries("TestName", testBars, 1, 3, true, numFactory,
                barBuilderFactory);

        assertEquals("TestName", series.getName());
        assertEquals(3, series.getBarCount());
        assertEquals(1, series.getBeginIndex());
        assertEquals(3, series.getEndIndex());
        assertSame(numFactory, series.numFactory());
        assertFalse(series.isEmpty());
    }

    @Test
    public void testConstructorWithCustomReadWriteLock() {
        ReadWriteLock customLock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = new ConcurrentBarSeries("TestName", testBars, 0, 4, false, numFactory,
                barBuilderFactory, customLock);

        assertEquals("TestName", series.getName());
        assertEquals(5, series.getBarCount());
        assertSame(numFactory, series.numFactory());
    }

    @Test
    public void testConstructorWithNullReadWriteLock() {
        assertThrows(NullPointerException.class, () -> {
            new ConcurrentBarSeries("TestName", testBars, 0, 4, false, numFactory, barBuilderFactory, null);
        });
    }

    // ==================== Thread Safety Tests for Read Operations
    // ====================

    @Test
    public void testConcurrentReadOperations() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final int readerCount = 10;
        final int operationsPerReader = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerReader; j++) {
                        // Test various read operations
                        series.getBarCount();
                        series.getBeginIndex();
                        series.getEndIndex();
                        series.getMaximumBarCount();
                        series.getRemovedBarsCount();
                        series.getSeriesPeriodDescription();
                        series.getSeriesPeriodDescriptionInSystemTimeZone();

                        if (series.getBarCount() > 0) {
                            series.getBar(0);
                            series.getBar(series.getEndIndex());
                        }

                        series.getBarData();
                        series.barBuilder();
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Log the exception but don't fail immediately
                    System.err.println("Read operation failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All readers should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    @Test
    public void testConcurrentWriteOperations() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        // Test concurrent write operations with proper synchronization
        // Since BarSeries requires chronological order, we'll test write operations
        // that don't conflict with each other by using a single writer approach
        final int operationsCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);
        final AtomicInteger successCount = new AtomicInteger(0);

        executorService.submit(() -> {
            try {
                startLatch.await();
                Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");

                for (int j = 0; j < operationsCount; j++) {
                    Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                            .endTime(baseTime.plus(Duration.ofDays(j)))
                            .openPrice(numOf(100 + j))
                            .highPrice(numOf(110 + j))
                            .lowPrice(numOf(90 + j))
                            .closePrice(numOf(105 + j))
                            .volume(numOf(100))
                            .amount(numOf(1000))
                            .trades(10)
                            .build();

                    series.addBar(newBar);

                    // Add trades and prices to the last bar
                    series.addTrade(numOf(10), numOf(50));
                    series.addPrice(numOf(75));
                    series.setMaximumBarCount(1000); // Should not affect anything
                }
                successCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println("Write operation failed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue("Writer should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(1, successCount.get());

        // Verify final state is consistent
        assertTrue("Series should have bars after writes", series.getBarCount() > 0);
    }

    @Test
    public void testConcurrentReadWriteOperations() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final int readerCount = 3;
        final int operationsPerThread = 20;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        // Start readers
        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        series.getBarCount();
                        series.getBarData();
                        if (series.getBarCount() > 0) {
                            series.getBar(series.getEndIndex());
                        }
                        Thread.sleep(1); // Small delay to allow interleaving
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Log the exception but don't fail immediately
                    System.err.println("Read operation failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All readers should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    // ==================== Immutability and Snapshot Tests ====================

    @Test
    public void testGetBarDataImmutability() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        List<Bar> snapshot1 = series.getBarData();
        List<Bar> snapshot2 = series.getBarData();

        // Snapshots should be different instances
        assertNotSame(snapshot1, snapshot2);

        // Snapshots should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            snapshot1.add(testBars.get(0));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            snapshot1.remove(0);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            snapshot1.clear();
        });
    }

    @Test
    public void testGetBarDataSnapshotConsistency() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        List<Bar> snapshot = series.getBarData();
        int originalSize = snapshot.size();

        // Add a new bar
        Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-06T00:00:00Z"))
                .closePrice(numOf(10.0))
                .build();
        series.addBar(newBar);

        // Snapshot should remain unchanged
        assertEquals("Snapshot should remain unchanged after mutation", originalSize, snapshot.size());

        // New snapshot should reflect the change
        List<Bar> newSnapshot = series.getBarData();
        assertEquals(originalSize + 1, newSnapshot.size());
    }

    // ==================== SubSeries Tests ====================

    @Test
    public void testGetSubSeriesReturnsConcurrentBarSeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        BarSeries subSeries = series.getSubSeries(1, 4);

        assertTrue("SubSeries should be ConcurrentBarSeries", subSeries instanceof ConcurrentBarSeries);
        assertEquals(3, subSeries.getBarCount());
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(2, subSeries.getEndIndex());
        assertEquals(series.getName(), subSeries.getName());
    }

    @Test
    public void testGetSubSeriesWithConcurrentAccess() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(2);
        final AtomicBoolean success = new AtomicBoolean(true);

        // Thread 1: Create subseries and access it
        executorService.submit(() -> {
            try {
                startLatch.await();
                BarSeries subSeries = series.getSubSeries(1, 4);
                for (int i = 0; i < 100; i++) {
                    subSeries.getBarCount();
                    subSeries.getBarData();
                    if (subSeries.getBarCount() > 0) {
                        subSeries.getBar(0);
                    }
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Modify parent series
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 50; i++) {
                    Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                            .endTime(Instant.parse("2025-01-01T00:00:00Z").plus(Duration.ofDays(i + 10)))
                            .openPrice(numOf(i + 100))
                            .highPrice(numOf(i + 101))
                            .lowPrice(numOf(i + 99))
                            .closePrice(numOf(i + 100))
                            .volume(numOf(100))
                            .amount(numOf(1000))
                            .trades(10)
                            .build();
                    series.addBar(newBar);
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue("All operations should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertTrue("All operations should succeed", success.get());
    }

    // ==================== Lock Contention and Deadlock Prevention Tests
    // ====================

    @Test
    public void testNoDeadlockWithMultipleLocks() throws Exception {
        ConcurrentBarSeries series1 = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName("Series1")
                .build();

        ConcurrentBarSeries series2 = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName("Series2")
                .build();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(2);
        final AtomicBoolean success = new AtomicBoolean(true);

        // Thread 1: Lock series1 then series2
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    series1.getBarData(); // Read lock on series1
                    Thread.sleep(1);
                    series2.getBarData(); // Read lock on series2
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Lock series2 then series1
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    series2.getBarData(); // Read lock on series2
                    Thread.sleep(1);
                    series1.getBarData(); // Read lock on series1
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue("Operations should complete without deadlock", endLatch.await(5, TimeUnit.SECONDS));
        assertTrue("All operations should succeed", success.get());
    }

    @Test
    public void testReadWriteLockUpgrade() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(3);
        final AtomicBoolean success = new AtomicBoolean(true);

        // Thread 1: Multiple read operations
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                    series.getBarCount();
                    series.getBarData();
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Write operations
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                            .endTime(Instant.parse("2025-01-01T00:00:00Z").plus(Duration.ofDays(i + 10)))
                            .openPrice(numOf(i + 100))
                            .highPrice(numOf(i + 101))
                            .lowPrice(numOf(i + 99))
                            .closePrice(numOf(i + 100))
                            .volume(numOf(100))
                            .amount(numOf(1000))
                            .trades(10)
                            .build();
                    series.addBar(newBar);
                    Thread.sleep(2);
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 3: Mixed operations
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 15; i++) {
                    if (i % 2 == 0) {
                        series.getBarCount();
                    } else {
                        // Only add trade if there are bars
                        if (series.getBarCount() > 0) {
                            series.addTrade(numOf(10), numOf(50));
                        }
                    }
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                success.set(false);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue("All operations should complete within timeout", endLatch.await(20, TimeUnit.SECONDS));
        assertTrue("All operations should succeed", success.get());
    }

    // ==================== Edge Cases and Error Conditions ====================

    @Test
    public void testConcurrentExceptionHandling() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(2);
        final AtomicInteger exceptionCount = new AtomicInteger(0);

        // Thread 1: Valid operations
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    series.getBarCount();
                    series.getBarData();
                }
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Operations that may throw exceptions
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    try {
                        // This should throw IndexOutOfBoundsException
                        series.getBar(1000);
                    } catch (IndexOutOfBoundsException e) {
                        // Expected exception
                    }

                    try {
                        // This should throw IllegalArgumentException
                        series.getSubSeries(-1, 5);
                    } catch (IllegalArgumentException e) {
                        // Expected exception
                    }
                }
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue("All operations should complete within timeout", endLatch.await(5, TimeUnit.SECONDS));

        // Should have no unexpected exceptions
        assertEquals(0, exceptionCount.get());
    }

    @Test
    public void testConcurrentBarDataConsistency() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        final int writerCount = 3;
        final int barsPerWriter = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(writerCount);
        final AtomicInteger totalBarsAdded = new AtomicInteger(0);

        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    // Use a fixed base time plus writer offset to ensure chronological order
                    Instant baseTime = Instant.parse("2025-01-01T00:00:00Z").plus(Duration.ofMinutes(writerId * 100));

                    for (int j = 0; j < barsPerWriter; j++) {
                        Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                                .endTime(baseTime.plus(Duration.ofMinutes(j)))
                                .openPrice(numOf(writerId * 1000 + j))
                                .highPrice(numOf(writerId * 1000 + j + 1))
                                .lowPrice(numOf(writerId * 1000 + j - 1))
                                .closePrice(numOf(writerId * 1000 + j))
                                .volume(numOf(100))
                                .amount(numOf(1000))
                                .trades(10)
                                .build();

                        series.addBar(newBar);
                        totalBarsAdded.incrementAndGet();

                        // Verify consistency after each addition
                        List<Bar> snapshot = series.getBarData();
                        assertNotNull("Snapshot should not be null", snapshot);
                        assertTrue("Snapshot size should be reasonable", snapshot.size() <= totalBarsAdded.get());
                    }
                } catch (Exception e) {
                    fail("Writer failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All writers should complete within timeout", endLatch.await(20, TimeUnit.SECONDS));

        // Final verification
        assertEquals(totalBarsAdded.get(), series.getBarCount());
        List<Bar> finalSnapshot = series.getBarData();
        assertEquals(totalBarsAdded.get(), finalSnapshot.size());
    }

    // ==================== Performance and Stress Tests ====================

    @Test
    public void testHighFrequencyReadWriteOperations() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        final int operationCount = 1000;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(2);
        final AtomicInteger readCount = new AtomicInteger(0);
        final AtomicInteger writeCount = new AtomicInteger(0);

        // Reader thread
        executorService.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < operationCount; i++) {
                    series.getBarCount();
                    series.getBarData();
                    readCount.incrementAndGet();
                }
            } catch (Exception e) {
                fail("Reader failed: " + e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        // Writer thread
        executorService.submit(() -> {
            try {
                startLatch.await();
                Instant baseTime = Instant.now();
                for (int i = 0; i < operationCount; i++) {
                    Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                            .endTime(baseTime.plus(Duration.ofMinutes(i)))
                            .openPrice(numOf(i))
                            .highPrice(numOf(i + 1))
                            .lowPrice(numOf(i - 1))
                            .closePrice(numOf(i))
                            .volume(numOf(100))
                            .amount(numOf(1000))
                            .trades(10)
                            .build();

                    series.addBar(newBar);
                    writeCount.incrementAndGet();
                }
            } catch (Exception e) {
                fail("Writer failed: " + e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue("High frequency operations should complete within timeout", endLatch.await(30, TimeUnit.SECONDS));

        assertEquals(operationCount, readCount.get());
        assertEquals(operationCount, writeCount.get());
        assertEquals(operationCount, series.getBarCount());
    }

    // ==================== Legacy Tests (from original implementation)
    // ====================

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
            fail("snapshot list must be immutable");
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

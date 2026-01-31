/*
 * SPDX-License-Identifier: MIT
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

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
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
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Comprehensive unit tests for {@link ConcurrentBarSeries} focusing on the
 * incremental, new logic that provides thread safety through ReadWriteLock.
 *
 * @since 0.22.2
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

    // ==================== getName() and numFactory() Tests ====================

    @Test
    public void testGetName() {
        ConcurrentBarSeries series = new ConcurrentBarSeries("MySeries", testBars);
        assertEquals("MySeries", series.getName());
    }

    @Test
    public void testGetNameWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeries("EmptySeries", Collections.emptyList());
        assertEquals("EmptySeries", series.getName());
    }

    @Test
    public void testGetNameConcurrentAccess() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeries("ConcurrentSeries", testBars);

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
                        String name = series.getName();
                        assertEquals("ConcurrentSeries", name);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("getName() operation failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All getName() operations should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    @Test
    public void testNumFactory() {
        ConcurrentBarSeries series = new ConcurrentBarSeries("TestSeries", testBars, 0, 4, false, numFactory,
                barBuilderFactory);
        assertSame(numFactory, series.numFactory());
    }

    @Test
    public void testNumFactoryWithDifferentFactories() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        NumFactory doubleFactory = DoubleNumFactory.getInstance();

        // Create test bars compatible with each factory
        List<Bar> decimalBars = new ArrayList<>();
        List<Bar> doubleBars = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");

        for (int i = 0; i < 5; i++) {
            Bar decimalBar = new TimeBarBuilder(decimalFactory).timePeriod(Duration.ofDays(1))
                    .endTime(baseTime.plus(Duration.ofDays(i)))
                    .openPrice(decimalFactory.numOf(i + 1))
                    .highPrice(decimalFactory.numOf(i + 2))
                    .lowPrice(decimalFactory.numOf(i))
                    .closePrice(decimalFactory.numOf(i + 1.5))
                    .volume(decimalFactory.numOf(i * 100))
                    .amount(decimalFactory.numOf(i * 1000))
                    .trades(i * 10)
                    .build();
            decimalBars.add(decimalBar);

            Bar doubleBar = new TimeBarBuilder(doubleFactory).timePeriod(Duration.ofDays(1))
                    .endTime(baseTime.plus(Duration.ofDays(i)))
                    .openPrice(doubleFactory.numOf(i + 1))
                    .highPrice(doubleFactory.numOf(i + 2))
                    .lowPrice(doubleFactory.numOf(i))
                    .closePrice(doubleFactory.numOf(i + 1.5))
                    .volume(doubleFactory.numOf(i * 100))
                    .amount(doubleFactory.numOf(i * 1000))
                    .trades(i * 10)
                    .build();
            doubleBars.add(doubleBar);
        }

        ConcurrentBarSeries decimalSeries = new ConcurrentBarSeries("DecimalSeries", decimalBars, 0, 4, false,
                decimalFactory, barBuilderFactory);
        ConcurrentBarSeries doubleSeries = new ConcurrentBarSeries("DoubleSeries", doubleBars, 0, 4, false,
                doubleFactory, barBuilderFactory);

        assertSame(decimalFactory, decimalSeries.numFactory());
        assertSame(doubleFactory, doubleSeries.numFactory());
        assertNotSame(decimalSeries.numFactory(), doubleSeries.numFactory());
    }

    @Test
    public void testNumFactoryConcurrentAccess() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeries("ConcurrentSeries", testBars, 0, 4, false, numFactory,
                barBuilderFactory);

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
                        NumFactory factory = series.numFactory();
                        assertSame(numFactory, factory);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("numFactory() operation failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All numFactory() operations should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    @Test
    public void testGetNameAndNumFactoryTogether() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeries("TestSeries", testBars, 0, 4, false, numFactory,
                barBuilderFactory);

        final int readerCount = 5;
        final int operationsPerReader = 50;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerReader; j++) {
                        String name = series.getName();
                        NumFactory factory = series.numFactory();
                        assertEquals("TestSeries", name);
                        assertSame(numFactory, factory);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Combined getName()/numFactory() operation failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All combined operations should complete within timeout", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
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
                        series.getName();
                        series.numFactory();
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
                .withMaxBarCount(1000)
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
    public void testGetSubSeriesPreservesMaxBarCountAndBarBuilderFactory() {
        BarBuilderFactory customFactory = new TimeBarBuilderFactory(false);
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(customFactory)
                .withMaxBarCount(3)
                .withBars(new ArrayList<>(testBars))
                .build();

        int startIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex() + 1;
        BarSeries subSeries = series.getSubSeries(startIndex, endIndex);

        assertEquals(series.getMaximumBarCount(), subSeries.getMaximumBarCount());

        subSeries.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(Instant.parse("2024-01-10T00:00:00Z"))
                .closePrice(numOf(50))
                .add();

        assertFalse(subSeries.getLastBar() instanceof RealtimeBar);
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

    @Test
    public void withReadLockSupportsRunnableAndSupplier() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        series.withReadLock(() -> assertEquals(0, series.getBarCount()));
        int count = series.withReadLock(series::getBarCount);
        assertEquals(0, count);
    }

    @Test
    public void withWriteLockSupportsRunnableAndSupplier() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.withWriteLock(() -> series.addBar(streamingBar(period, start, 100, 110, 90, 105, 5)));

        int count = series.withWriteLock(series::getBarCount);
        assertEquals(1, count);
        assertEquals(1, series.getBarCount());
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
                    Instant baseTime = Instant.parse("2025-01-01T00:00:00Z").plus(Duration.ofMinutes(writerId * 100L));

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

    // ==================== Streaming Bar Integration Tests ====================

    @Test
    public void ingestStreamingBarAppendsBar() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        var result = series.ingestStreamingBar(streamingBar(period, start, 100, 110, 90, 105, 5));

        assertEquals(1, series.getBarCount());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.APPENDED, result.action());
        assertEquals(0, result.index());
        var bar = series.getLastBar();
        assertEquals(start.plus(period), bar.getEndTime());
        assertEquals(numOf(105), bar.getClosePrice());
        assertEquals(numOf(90), bar.getLowPrice());
        assertEquals(numOf(5), bar.getVolume());
    }

    @Test
    public void ingestStreamingBarReplacesLatestInterval() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.ingestStreamingBar(streamingBar(period, start, 100, 110, 90, 105, 5));
        var result = series.ingestStreamingBar(streamingBar(period, start, 105, 120, 95, 115, 8));

        assertEquals(1, series.getBarCount());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.REPLACED_LAST, result.action());
        assertEquals(0, result.index());
        var bar = series.getLastBar();
        assertEquals(numOf(115), bar.getClosePrice());
        assertEquals(numOf(8), bar.getVolume());
    }

    @Test
    public void ingestStreamingBarUpdatesOlderInterval() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");
        var second = start.plus(period);

        series.ingestStreamingBar(streamingBar(period, start, 100, 110, 90, 105, 5));
        series.ingestStreamingBar(streamingBar(period, second, 105, 120, 95, 115, 8));

        var result = series.ingestStreamingBar(streamingBar(period, start, 200, 210, 190, 205, 15));

        assertEquals(2, series.getBarCount());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.REPLACED_HISTORICAL, result.action());
        assertEquals(0, result.index());
        assertEquals(numOf(205), series.getBar(0).getClosePrice());
        assertEquals(numOf(115), series.getBar(1).getClosePrice());
    }

    @Test
    public void ingestStreamingBarsSortNewestFirstPayloads() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var first = Instant.parse("2024-01-01T00:00:00Z");
        var second = first.plus(period);

        var newestFirst = List.of(streamingBar(period, second, 105, 115, 95, 110, 8),
                streamingBar(period, first, 100, 110, 90, 105, 5));

        var results = series.ingestStreamingBars(newestFirst);

        assertEquals(2, series.getBarCount());
        assertEquals(2, results.size());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.APPENDED, results.get(0).action());
        assertEquals(0, results.get(0).index());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.APPENDED, results.get(1).action());
        assertEquals(1, results.get(1).index());
        assertEquals(first.plus(period), series.getBar(0).getEndTime());
        assertEquals(second.plus(period), series.getBar(1).getEndTime());
    }

    @Test
    public void ingestStreamingBarReportsSeriesIndexAfterEviction() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withMaxBarCount(2)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.ingestStreamingBar(streamingBar(period, start, 100, 110, 90, 105, 5));
        series.ingestStreamingBar(streamingBar(period, start.plus(period), 105, 115, 95, 110, 6));
        series.ingestStreamingBar(streamingBar(period, start.plus(period).plus(period), 110, 120, 100, 115, 7));

        var result = series.ingestStreamingBar(streamingBar(period, start.plus(period), 200, 210, 190, 205, 8));

        assertEquals(2, series.getBarCount());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.REPLACED_HISTORICAL, result.action());
        assertEquals(1, result.index());
        assertEquals(numOf(205), series.getBar(1).getClosePrice());
    }

    @Test
    public void ingestStreamingBarRejectsMismatchedNumFactory() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        var foreignFactory = numFactory instanceof DoubleNumFactory ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        var foreignBar = new TimeBarBuilder(foreignFactory).timePeriod(period)
                .beginTime(start)
                .endTime(start.plus(period))
                .openPrice(1)
                .highPrice(2)
                .lowPrice(0)
                .closePrice(1)
                .volume(1)
                .build();

        assertThrows(IllegalArgumentException.class, () -> series.ingestStreamingBar(foreignBar));
    }

    // ==================== Streaming Trade Integration Tests ====================

    @Test
    public void ingestTradeBuildsTimeBarFromTrades() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:30Z");
        var alignedStart = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        series.ingestTrade(start, 1, 100);
        series.ingestTrade(start.plusSeconds(10), 2, 105);
        series.ingestTrade(start.plusSeconds(20), 1, 95);

        assertEquals(1, series.getBarCount());
        var bar = series.getLastBar();
        assertEquals(alignedStart, bar.getBeginTime());
        assertEquals(alignedStart.plus(period), bar.getEndTime());
        assertEquals(numOf(100), bar.getOpenPrice());
        assertEquals(numOf(105), bar.getHighPrice());
        assertEquals(numOf(95), bar.getLowPrice());
        assertEquals(numOf(95), bar.getClosePrice());
        assertEquals(numOf(4), bar.getVolume());
        assertEquals(numOf(405), bar.getAmount());
        assertEquals(3, bar.getTrades());
    }

    @Test
    public void ingestTradeRollsOverTimePeriods() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        series.ingestTrade(start, 1, 100);
        series.ingestTrade(start.plusSeconds(70), 2, 110);

        assertEquals(2, series.getBarCount());
        var first = series.getBar(0);
        assertEquals(start, first.getBeginTime());
        assertEquals(start.plus(period), first.getEndTime());
        var second = series.getBar(1);
        assertEquals(start.plus(period), second.getBeginTime());
        assertEquals(start.plus(period.multipliedBy(2)), second.getEndTime());
        assertEquals(numOf(110), second.getClosePrice());
        assertEquals(numOf(2), second.getVolume());
    }

    @Test
    public void ingestTradeCapturesSideAndLiquidity() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:30Z");
        var alignedStart = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        series.ingestTrade(start, 1, 100, RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        series.ingestTrade(start.plusSeconds(10), 2, 110, RealtimeBar.Side.SELL, RealtimeBar.Liquidity.TAKER);

        assertEquals(1, series.getBarCount());
        var bar = series.getLastBar();
        assertTrue(bar instanceof RealtimeBar);
        var realtimeBar = (RealtimeBar) bar;
        assertEquals(alignedStart, realtimeBar.getBeginTime());
        assertEquals(alignedStart.plus(period), realtimeBar.getEndTime());
        assertTrue(realtimeBar.hasSideData());
        assertTrue(realtimeBar.hasLiquidityData());
        assertEquals(numOf(1), realtimeBar.getBuyVolume());
        assertEquals(numOf(2), realtimeBar.getSellVolume());
        assertEquals(numOf(100), realtimeBar.getBuyAmount());
        assertEquals(numOf(220), realtimeBar.getSellAmount());
        assertEquals(1, realtimeBar.getBuyTrades());
        assertEquals(1, realtimeBar.getSellTrades());
        assertEquals(numOf(1), realtimeBar.getMakerVolume());
        assertEquals(numOf(2), realtimeBar.getTakerVolume());
        assertEquals(numOf(100), realtimeBar.getMakerAmount());
        assertEquals(numOf(220), realtimeBar.getTakerAmount());
        assertEquals(1, realtimeBar.getMakerTrades());
        assertEquals(1, realtimeBar.getTakerTrades());
    }

    @Test
    public void ingestTradeSupportsOptionalSideAndLiquidity() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        series.ingestTrade(start, 1, 100, null, RealtimeBar.Liquidity.MAKER);
        series.ingestTrade(start.plusSeconds(10), 2, 110, RealtimeBar.Side.BUY, null);

        var bar = (RealtimeBar) series.getLastBar();
        assertTrue(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(numOf(2), bar.getBuyVolume());
        assertEquals(numOf(0), bar.getSellVolume());
        assertEquals(numOf(220), bar.getBuyAmount());
        assertEquals(numOf(0), bar.getSellAmount());
        assertEquals(1, bar.getBuyTrades());
        assertEquals(0, bar.getSellTrades());
        assertEquals(numOf(1), bar.getMakerVolume());
        assertEquals(numOf(0), bar.getTakerVolume());
        assertEquals(numOf(100), bar.getMakerAmount());
        assertEquals(numOf(0), bar.getTakerAmount());
        assertEquals(1, bar.getMakerTrades());
        assertEquals(0, bar.getTakerTrades());
    }

    @Test
    public void ingestTradeResetsSideAndLiquidityAcrossBars() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        series.ingestTrade(start, 1, 100, RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        series.ingestTrade(start.plusSeconds(70), 2, 110, null, null);

        assertEquals(2, series.getBarCount());
        var first = (RealtimeBar) series.getBar(0);
        assertTrue(first.hasSideData());
        assertTrue(first.hasLiquidityData());

        var second = (RealtimeBar) series.getBar(1);
        assertFalse(second.hasSideData());
        assertFalse(second.hasLiquidityData());
        assertEquals(numOf(0), second.getBuyVolume());
        assertEquals(numOf(0), second.getMakerVolume());
        assertEquals(numOf(2), second.getVolume());
        assertEquals(numOf(220), second.getAmount());
        assertEquals(1, second.getTrades());
    }

    @Test
    public void ingestTradeRequiresTimePeriod() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        var start = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> series.ingestTrade(start, 1, 100));
    }

    @Test
    public void ingestTradeRejectsMismatchedNumFactoryWithSide() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var start = Instant.parse("2024-01-01T00:00:00Z");

        var foreignFactory = numFactory instanceof DoubleNumFactory ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        var foreignVolume = foreignFactory.numOf(1);
        var foreignPrice = foreignFactory.numOf(100);

        assertThrows(IllegalArgumentException.class, () -> series.ingestTrade(start, foreignVolume, foreignPrice,
                RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER));
    }

    @Test
    public void ingestTradeRejectsNullArgumentsWithSide() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var start = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(NullPointerException.class,
                () -> series.ingestTrade(null, 1, 100, RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER));
        assertThrows(NullPointerException.class,
                () -> series.ingestTrade(start, (Number) null, 100, RealtimeBar.Side.BUY, null));
        assertThrows(NullPointerException.class,
                () -> series.ingestTrade(start, 1, (Number) null, null, RealtimeBar.Liquidity.MAKER));
    }

    @Test
    public void ingestTradeRejectsOutOfOrderTimestamp() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:01:30Z");

        series.tradeBarBuilder().timePeriod(period);

        series.ingestTrade(start, 1, 100);

        assertThrows(IllegalArgumentException.class, () -> series.ingestTrade(start.minusSeconds(120), 1, 100));
    }

    // ==================== Serialization Tests ====================

    @Test
    public void serializeAndDeserializeReinitializesLocksAndBuilders() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(true))
                .build();
        var period = Duration.ofMinutes(1);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);
        series.ingestTrade(start, 1, 100);

        ConcurrentBarSeries restored = serializeRoundTrip(series);

        assertNotSame(series, restored);
        assertEquals(series.getBarCount(), restored.getBarCount());
        assertEquals(series.getEndIndex(), restored.getEndIndex());

        restored.tradeBarBuilder().timePeriod(period);
        restored.ingestTrade(start.plusSeconds(60), 1, 110);

        assertEquals(series.getBarCount() + 1, restored.getBarCount());
    }

    @Test
    public void serializeAndDeserializePreservesMaxBarCount() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withMaxBarCount(10)
                .withBars(new ArrayList<>(testBars))
                .build();

        ConcurrentBarSeries restored = serializeRoundTrip(series);

        assertEquals(series.getMaximumBarCount(), restored.getMaximumBarCount());
        assertEquals(10, restored.getMaximumBarCount());
    }

    @Test
    public void serializeAndDeserializeEmptySeries() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        ConcurrentBarSeries restored = serializeRoundTrip(series);

        assertEquals(0, restored.getBarCount());
        assertEquals(-1, restored.getBeginIndex());
        assertEquals(-1, restored.getEndIndex());
        assertTrue(restored.isEmpty());
    }

    // ==================== getFirstBar() and getLastBar() Tests
    // ====================

    @Test
    public void testGetFirstBar() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        Bar firstBar = series.getFirstBar();
        assertNotNull(firstBar);
        assertEquals(testBars.get(0), firstBar);
        assertEquals(series.getBar(series.getBeginIndex()), firstBar);
    }

    @Test
    public void testGetLastBar() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        Bar lastBar = series.getLastBar();
        assertNotNull(lastBar);
        assertEquals(testBars.get(testBars.size() - 1), lastBar);
        assertEquals(series.getBar(series.getEndIndex()), lastBar);
    }

    @Test
    public void testGetFirstBarWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        assertThrows(IndexOutOfBoundsException.class, () -> series.getFirstBar());
    }

    @Test
    public void testGetLastBarWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        assertThrows(IndexOutOfBoundsException.class, () -> series.getLastBar());
    }

    @Test
    public void testGetFirstBarConcurrentAccess() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final int readerCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        Bar firstBar = series.getFirstBar();
                        assertNotNull(firstBar);
                        assertEquals(testBars.get(0), firstBar);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All getFirstBar() operations should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    // ==================== getSeriesPeriodDescription() Tests ====================

    @Test
    public void testGetSeriesPeriodDescription() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        String description = series.getSeriesPeriodDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty());
    }

    @Test
    public void testGetSeriesPeriodDescriptionWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        String description = series.getSeriesPeriodDescription();
        assertNotNull(description);
    }

    @Test
    public void testGetSeriesPeriodDescriptionInSystemTimeZone() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        String description = series.getSeriesPeriodDescriptionInSystemTimeZone();
        assertNotNull(description);
        assertFalse(description.isEmpty());
    }

    @Test
    public void testGetSeriesPeriodDescriptionInSystemTimeZoneWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        String description = series.getSeriesPeriodDescriptionInSystemTimeZone();
        assertNotNull(description);
    }

    @Test
    public void testGetSeriesPeriodDescriptionConcurrentAccess() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final int readerCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        String desc1 = series.getSeriesPeriodDescription();
                        String desc2 = series.getSeriesPeriodDescriptionInSystemTimeZone();
                        assertNotNull(desc1);
                        assertNotNull(desc2);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All period description operations should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    // ==================== addPrice() Tests ====================

    @Test
    public void testAddPrice() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        Instant now = Instant.now();
        series.barBuilder().endTime(now).closePrice(numOf(100)).add();

        series.addPrice(numOf(105));
        Bar lastBar = series.getLastBar();
        assertEquals(numOf(105), lastBar.getClosePrice());
    }

    @Test
    public void testAddPriceWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        assertThrows(IndexOutOfBoundsException.class, () -> series.addPrice(numOf(100)));
    }

    @Test
    public void testAddPriceConcurrentAccess() throws Exception {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        Instant now = Instant.now();
        series.barBuilder().endTime(now).closePrice(numOf(100)).add();

        final int writerCount = 5;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(writerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < writerCount; i++) {
            final int priceOffset = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        series.addPrice(numOf(100 + priceOffset + j));
                        Thread.sleep(1);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All addPrice() operations should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(writerCount, successCount.get());
    }

    // ==================== addBar() with replace flag Tests ====================

    @Test
    public void testAddBarWithReplace() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        Bar firstBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(now)
                .closePrice(numOf(100))
                .build();

        series.addBar(firstBar, false);
        assertEquals(1, series.getBarCount());
        assertEquals(numOf(100), series.getBar(0).getClosePrice());

        Bar replacementBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(now)
                .closePrice(numOf(200))
                .build();

        series.addBar(replacementBar, true);
        assertEquals(1, series.getBarCount());
        assertEquals(numOf(200), series.getBar(0).getClosePrice());
    }

    @Test
    public void testAddBarWithoutReplace() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        Bar firstBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(now)
                .closePrice(numOf(100))
                .build();

        series.addBar(firstBar, false);
        assertEquals(1, series.getBarCount());

        Bar secondBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(now.plus(Duration.ofMinutes(1)))
                .closePrice(numOf(200))
                .build();

        series.addBar(secondBar, false);
        assertEquals(2, series.getBarCount());
        assertEquals(numOf(100), series.getBar(0).getClosePrice());
        assertEquals(numOf(200), series.getBar(1).getClosePrice());
    }

    // ==================== StreamingBarIngestResult Validation Tests
    // ====================

    @Test
    public void testStreamingBarIngestResultRejectsNegativeIndex() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ConcurrentBarSeries.StreamingBarIngestResult(ConcurrentBarSeries.StreamingBarIngestAction.APPENDED, -1);
        });
    }

    @Test
    public void testStreamingBarIngestResultRejectsNullAction() {
        assertThrows(NullPointerException.class, () -> {
            new ConcurrentBarSeries.StreamingBarIngestResult(null, 0);
        });
    }

    @Test
    public void testStreamingBarIngestResultAcceptsZeroIndex() {
        var result = new ConcurrentBarSeries.StreamingBarIngestResult(
                ConcurrentBarSeries.StreamingBarIngestAction.APPENDED, 0);
        assertEquals(0, result.index());
        assertEquals(ConcurrentBarSeries.StreamingBarIngestAction.APPENDED, result.action());
    }

    // ==================== ingestStreamingBars() Edge Cases ====================

    @Test
    public void testIngestStreamingBarsWithNullCollection() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        List<ConcurrentBarSeries.StreamingBarIngestResult> results = series.ingestStreamingBars(null);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testIngestStreamingBarsWithEmptyCollection() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        List<ConcurrentBarSeries.StreamingBarIngestResult> results = series
                .ingestStreamingBars(Collections.emptyList());
        assertTrue(results.isEmpty());
    }

    @Test
    public void testIngestStreamingBarsFiltersNullBars() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();
        var period = Duration.ofSeconds(60);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        var barsWithNulls = new ArrayList<Bar>();
        barsWithNulls.add(null);
        barsWithNulls.add(streamingBar(period, start, 100, 110, 90, 105, 5));
        barsWithNulls.add(null);
        barsWithNulls.add(streamingBar(period, start.plus(period), 105, 115, 95, 110, 6));

        var results = series.ingestStreamingBars(barsWithNulls);

        assertEquals(2, series.getBarCount());
        assertEquals(2, results.size());
    }

    // ==================== getSubSeries() Edge Cases ====================

    @Test
    public void testGetSubSeriesWithEmptySeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        BarSeries subSeries = series.getSubSeries(0, 1);
        assertTrue(subSeries instanceof ConcurrentBarSeries);
        assertEquals(0, subSeries.getBarCount());
        assertEquals(-1, subSeries.getBeginIndex());
        assertEquals(-1, subSeries.getEndIndex());
    }

    @Test
    public void testGetSubSeriesWithStartIndexEqualsEndIndex() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        assertThrows(IllegalArgumentException.class, () -> series.getSubSeries(2, 2));
    }

    @Test
    public void testGetSubSeriesWithStartIndexGreaterThanEndIndex() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        assertThrows(IllegalArgumentException.class, () -> series.getSubSeries(3, 2));
    }

    @Test
    public void testGetSubSeriesWithNegativeStartIndex() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        assertThrows(IllegalArgumentException.class, () -> series.getSubSeries(-1, 3));
    }

    @Test
    public void testGetSubSeriesInheritsConstrainedBehavior() {
        var bars = new ArrayList<>(testBars.subList(0, 2));
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(bars)
                .build();

        BarSeries subSeries = series.getSubSeries(0, 2);
        assertThrows(IllegalStateException.class, () -> subSeries.setMaximumBarCount(10));
    }

    @Test
    public void testGetSubSeriesInheritsMaxBarCountConfiguration() {
        var bars = new ArrayList<>(testBars.subList(0, 2));
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(bars)
                .withMaxBarCount(10)
                .build();

        BarSeries subSeries = series.getSubSeries(0, 2);
        subSeries.setMaximumBarCount(5);
        assertEquals(5, subSeries.getMaximumBarCount());
    }

    // ==================== tradeBarBuilder() Lazy Initialization Tests
    // ====================

    @Test
    public void testTradeBarBuilderLazyInitialization() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        // First call should initialize
        BarBuilder builder1 = series.tradeBarBuilder();
        assertNotNull(builder1);

        // Second call should return same instance
        BarBuilder builder2 = series.tradeBarBuilder();
        assertSame(builder1, builder2);
    }

    @Test
    public void testTradeBarBuilderConcurrentInitialization() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        final int threadCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final List<BarBuilder> builders = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    BarBuilder builder = series.tradeBarBuilder();
                    builders.add(builder);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All tradeBarBuilder() calls should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(threadCount, builders.size());

        // All builders should be the same instance (lazy initialization with
        // double-check)
        BarBuilder firstBuilder = builders.get(0);
        for (BarBuilder builder : builders) {
            assertSame(firstBuilder, builder);
        }
    }

    // ==================== withReadLock() and withWriteLock() Null Handling
    // ====================

    @Test
    public void testWithReadLockRejectsNullRunnable() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        assertThrows(NullPointerException.class, () -> series.withReadLock((Runnable) null));
    }

    @Test
    public void testWithReadLockRejectsNullSupplier() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        Supplier<Integer> nullSupplier = null;
        assertThrows(NullPointerException.class, () -> series.withReadLock(nullSupplier));
    }

    @Test
    public void testWithWriteLockRejectsNullRunnable() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        assertThrows(NullPointerException.class, () -> series.withWriteLock((Runnable) null));
    }

    @Test
    public void testWithWriteLockRejectsNullSupplier() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .build();

        Supplier<Integer> nullSupplier = null;
        assertThrows(NullPointerException.class, () -> series.withWriteLock(nullSupplier));
    }

    // ==================== setMaximumBarCount() Concurrent Access
    // ====================

    @Test
    public void testSetMaximumBarCountConcurrentAccess() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .withMaxBarCount(1000)
                .build();

        final int writerCount = 5;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(writerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < writerCount; i++) {
            final int maxCount = 100 + i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        series.setMaximumBarCount(maxCount + j);
                        int currentMax = series.getMaximumBarCount();
                        assertTrue("Max bar count should be set", currentMax > 0);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All setMaximumBarCount() operations should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(writerCount, successCount.get());
    }

    // ==================== barBuilder() Concurrent Access ====================

    @Test
    public void testBarBuilderConcurrentAccess() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .build();

        final int readerCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        BarBuilder builder = series.barBuilder();
                        assertNotNull(builder);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All barBuilder() operations should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    // ==================== getRemovedBarsCount() Concurrent Access
    // ====================

    @Test
    public void testGetRemovedBarsCountConcurrentAccess() throws Exception {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(new ArrayList<>(testBars))
                .withMaxBarCount(3)
                .build();

        // Add more bars to trigger removal
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            series.barBuilder().endTime(now.plus(Duration.ofDays(i))).closePrice(numOf(i)).add();
        }

        final int readerCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readerCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        int removedCount = series.getRemovedBarsCount();
                        assertTrue("Removed count should be non-negative", removedCount >= 0);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All getRemovedBarsCount() operations should complete", endLatch.await(10, TimeUnit.SECONDS));
        assertEquals(readerCount, successCount.get());
    }

    // ==================== Trade Ingestion Gap Handling Tests ====================

    @Test
    public void testIngestTradeOmitsGaps() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        var period = Duration.ofMinutes(1);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        // First trade at 00:00:30
        series.ingestTrade(start.plusSeconds(30), 1, 100);
        assertEquals(1, series.getBarCount());

        // Second trade at 00:02:30 (skips 00:01:00 bar)
        series.ingestTrade(start.plusSeconds(150), 2, 110);
        assertEquals(2, series.getBarCount());

        // Verify no empty bar was inserted
        Bar firstBar = series.getBar(0);
        assertEquals(start, firstBar.getBeginTime());
        assertEquals(start.plus(period), firstBar.getEndTime());

        Bar secondBar = series.getBar(1);
        assertEquals(start.plus(period.multipliedBy(2)), secondBar.getBeginTime());
        assertEquals(start.plus(period.multipliedBy(3)), secondBar.getEndTime());
    }

    @Test
    public void testIngestTradeHandlesLargeGaps() {
        var series = new ConcurrentBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        var period = Duration.ofMinutes(1);
        var start = Instant.parse("2024-01-01T00:00:00Z");

        series.tradeBarBuilder().timePeriod(period);

        // First trade at 00:00:30
        series.ingestTrade(start.plusSeconds(30), 1, 100);
        assertEquals(1, series.getBarCount());

        // Second trade at 00:10:30 (skips 9 bars)
        series.ingestTrade(start.plusSeconds(630), 2, 110);
        assertEquals(2, series.getBarCount());

        // Verify only 2 bars exist, no empty bars inserted
        assertEquals(2, series.getBarCount());
        Bar secondBar = series.getBar(1);
        assertEquals(start.plus(period.multipliedBy(10)), secondBar.getBeginTime());
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

    private static ConcurrentBarSeries serializeRoundTrip(final ConcurrentBarSeries series) throws Exception {
        final byte[] payload;
        try (final var outputStream = new ByteArrayOutputStream()) {
            try (final var objectOutputStream = new ObjectOutputStream(outputStream)) {
                objectOutputStream.writeObject(series);
                payload = outputStream.toByteArray();
            }
        }
        try (final var inputStream = new ByteArrayInputStream(payload)) {
            try (final var objectInputStream = new ObjectInputStream(inputStream)) {
                return (ConcurrentBarSeries) objectInputStream.readObject();
            }
        }
    }

    private Bar streamingBar(final Duration period, final Instant start, final double open, final double high,
            final double low, final double close, final double volume) {
        return new TimeBarBuilder(numFactory).timePeriod(period)
                .beginTime(start)
                .endTime(start.plus(period))
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(volume)
                .build();
    }
}

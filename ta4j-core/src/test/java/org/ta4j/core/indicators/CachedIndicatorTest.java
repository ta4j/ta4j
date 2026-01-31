/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.ZLEMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class CachedIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CachedIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
    }

    @Test
    public void ifCacheWorks() {
        var sma = new SMAIndicator(new ClosePriceIndicator(series), 3);
        Num firstTime = sma.getValue(4);
        Num secondTime = sma.getValue(4);
        assertEquals(firstTime, secondTime);
    }

    @Test // should be not null
    public void getValueWithNullBarSeries() {

        ConstantIndicator<Num> constant = new ConstantIndicator<>(
                new BaseBarSeriesBuilder().withNumFactory(numFactory).build(), numFactory.numOf(10));
        assertEquals(numFactory.numOf(10), constant.getValue(0));
        assertEquals(numFactory.numOf(10), constant.getValue(100));
        assertNotNull(constant.getBarSeries());

        SMAIndicator sma = new SMAIndicator(constant, 10);
        assertEquals(numFactory.numOf(10), sma.getValue(0));
        assertEquals(numFactory.numOf(10), sma.getValue(100));
        assertNotNull(sma.getBarSeries());
    }

    @Test
    public void getValueWithCacheLengthIncrease() {
        double[] data = new double[200];
        Arrays.fill(data, 10);
        SMAIndicator sma = new SMAIndicator(
                new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build()),
                100);
        assertNumEquals(10, sma.getValue(105));
    }

    @Test
    public void getValueWithOldResultsRemoval() {
        double[] data = new double[20];
        Arrays.fill(data, 1);
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 10);
        assertNumEquals(1, sma.getValue(5));
        assertNumEquals(1, sma.getValue(10));
        barSeries.setMaximumBarCount(12);
        assertNumEquals(1, sma.getValue(19));
    }

    @Test
    public void strategyExecutionOnCachedIndicatorAndLimitedBarSeries() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, 1, 2, 3, 4, 5, 6, 7)
                .build();
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 2);
        // Theoretical values for SMA(2) cache: 0, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5
        barSeries.setMaximumBarCount(6);
        // Theoretical values for SMA(2) cache: null, null, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        Strategy strategy = new BaseStrategy(new OverIndicatorRule(sma, numFactory.numOf(3)),
                new UnderIndicatorRule(sma, numFactory.numOf(3)));
        // Theoretical shouldEnter results: false, false, false, false, true, true,
        // true, true
        // Theoretical shouldExit results: false, false, true, true, false, false,
        // false, false

        // As we return the first bar/result found for the removed bars:
        // -> Approximated values for ClosePrice cache: 2, 2, 2, 3, 4, 5, 6, 7
        // -> Approximated values for SMA(2) cache: 2, 2, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        // Then enters/exits are also approximated:
        // -> shouldEnter results: false, false, false, false, true, true, true, true
        // -> shouldExit results: true, true, true, true, false, false, false, false

        assertFalse(strategy.shouldEnter(0));
        assertTrue(strategy.shouldExit(0));
        assertFalse(strategy.shouldEnter(1));
        assertTrue(strategy.shouldExit(1));
        assertFalse(strategy.shouldEnter(2));
        assertTrue(strategy.shouldExit(2));
        assertFalse(strategy.shouldEnter(3));
        assertTrue(strategy.shouldExit(3));
        assertTrue(strategy.shouldEnter(4));
        assertFalse(strategy.shouldExit(4));
        assertTrue(strategy.shouldEnter(5));
        assertFalse(strategy.shouldExit(5));
        assertTrue(strategy.shouldEnter(6));
        assertFalse(strategy.shouldExit(6));
        assertTrue(strategy.shouldEnter(7));
        assertFalse(strategy.shouldExit(7));
    }

    @Test
    public void getValueOnResultsCalculatedFromRemovedBarsShouldReturnFirstRemainingResult() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1).build();
        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getRemovedBarsCount());

        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 2);
        for (int i = 0; i < 5; i++) {
            assertNumEquals(1, sma.getValue(i));
        }
    }

    @Test
    public void prunedIndexCacheInvalidatesWhenRemovedBarsCountChanges() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        ClosePriceCountingIndicator indicator = new ClosePriceCountingIndicator(barSeries);

        assertNumEquals(1, indicator.getValue(0));
        assertEquals(1, indicator.getCalculationCount());

        // Force removal of the first bar and thus change the "first available bar" for
        // index 0.
        barSeries.setMaximumBarCount(2);
        assertEquals(1, barSeries.getRemovedBarsCount());

        assertNumEquals(2, indicator.getValue(0));
        assertEquals(2, indicator.getCalculationCount());

        // Subsequent hits should reuse the pruned-index cache for the new
        // removedBarsCount.
        assertNumEquals(2, indicator.getValue(0));
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void recursiveCachedIndicatorOnMovingBarSeriesShouldNotCauseStackOverflow() {
        // Added to check issue #120: https://github.com/mdeverdelhan/ta4j/issues/120
        // See also: CachedIndicator#getValue(int index)
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        series.setMaximumBarCount(5);
        assertEquals(5, series.getBarCount());

        ZLEMAIndicator zlema = new ZLEMAIndicator(new ClosePriceIndicator(series), 1);
        try {
            assertNumEquals(4996, zlema.getValue(8));
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    @Test
    public void leaveLastBarUncached() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var smaIndicator = new SMAIndicator(new ClosePriceIndicator(barSeries), 5);
        assertNumEquals(4998.0, smaIndicator.getValue(barSeries.getEndIndex()));
        barSeries.getLastBar().addTrade(numOf(10), numOf(5));

        // (4996 + 4997 + 4998 + 4999 + 5) / 5
        assertNumEquals(3999, smaIndicator.getValue(barSeries.getEndIndex()));

    }

    @Test
    public void concurrentAccessCachesSingleComputationPerIndex() throws InterruptedException {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    indicator.getValue(4);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Concurrent tasks did not finish in time", done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("Only one calculation should be performed for the requested index despite concurrent access.", 1,
                indicator.getCalculationCount());
    }

    @Test
    public void lastBarCacheIsThreadSafeAcrossThreads() throws InterruptedException {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        CountingIndicator indicator = new CountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    indicator.getValue(endIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Concurrent tasks did not finish in time", done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("Only one calculation should be performed for the last bar despite concurrent access.", 1,
                indicator.getCalculationCount());

        // Mutate last bar to force invalidation and ensure a recomputation occurs
        barSeries.getLastBar().addTrade(numOf(1), numOf(7));
        indicator.getValue(endIndex);
        assertEquals("Mutation should trigger recomputation of last-bar cache.", 2, indicator.getCalculationCount());
    }

    @Test
    public void lastBarComputationDoesNotDeadlockWhenCacheWriteLockHeldAndAnotherLastBarComputationIsInFlight()
            throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();

        WriteLockedLastBarIndicator indicator = new WriteLockedLastBarIndicator(barSeries, endIndex);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Num> writeLockedFuture = executor.submit(() -> indicator.getValue(endIndex - 1));
            assertTrue("Write-locked calculation did not start in time",
                    indicator.writeLockedCalculationStarted.await(30, TimeUnit.SECONDS));

            Future<Num> lastBarFuture = executor.submit(() -> indicator.getValue(endIndex));
            assertTrue("Last-bar calculation did not start in time",
                    indicator.lastBarCalculationStarted.await(30, TimeUnit.SECONDS));

            assertNumEquals(endIndex - 1, writeLockedFuture.get(30, TimeUnit.SECONDS));
            assertNumEquals(endIndex, lastBarFuture.get(30, TimeUnit.SECONDS));
            assertTrue("Expected last-bar read to occur while holding cache write lock",
                    indicator.writeLockedDuringLastBarRead.get());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void lastBarCacheInvalidatesWhenLastBarIsReplacedDuringRead() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        CountDownLatch tradesReadStarted = new CountDownLatch(1);
        CountDownLatch allowTradesRead = new CountDownLatch(1);
        BlockingTradesBar blockingBar = new BlockingTradesBar(barSeries.barBuilder()
                .closePrice(1)
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .volume(0)
                .amount(0)
                .trades(0)
                .build(), tradesReadStarted, allowTradesRead);
        barSeries.addBar(blockingBar);

        ClosePriceCountingIndicator indicator = new ClosePriceCountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        assertNumEquals(1, indicator.getValue(endIndex));
        assertEquals(1, indicator.getCalculationCount());

        blockingBar.enableBlocking();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(endIndex));
            assertTrue("Expected last-bar cache read to start in time", tradesReadStarted.await(30, TimeUnit.SECONDS));

            barSeries.addBar(barSeries.barBuilder()
                    .closePrice(2)
                    .openPrice(2)
                    .highPrice(2)
                    .lowPrice(2)
                    .volume(0)
                    .amount(0)
                    .trades(0)
                    .build(), true);

            allowTradesRead.countDown();

            assertNumEquals(2, future.get(30, TimeUnit.SECONDS));
            assertEquals(2, indicator.getCalculationCount());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void highestResultIndexNotAdvancedWhenCalculationFails() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int failIndex = 1; // non-last index to avoid last-bar path
        FailingIndicator indicator = new FailingIndicator(barSeries, failIndex);

        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(-1, indicator.getCacheHighestResultIndex());

        try {
            indicator.getValue(failIndex);
            fail("Expected calculation to throw on first attempt");
        } catch (RuntimeException expected) {
            // expected path
        }

        // highestResultIndex should not advance when calculation fails
        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(-1, indicator.getCacheHighestResultIndex());
        assertEquals(1, indicator.getCalculationCount());

        // Next call should compute successfully and advance both trackers
        assertNumEquals(failIndex, indicator.getValue(failIndex));
        assertEquals(failIndex, indicator.getHighestResultIndex());
        assertEquals(failIndex, indicator.getCacheHighestResultIndex());
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void lastBarCacheDoesNotGetStuckWhenCalculationFails() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();
        FailingIndicator indicator = new FailingIndicator(barSeries, endIndex);

        assertEquals(-1, indicator.getHighestResultIndex());

        try {
            indicator.getValue(endIndex);
            fail("Expected calculation to throw on first attempt");
        } catch (RuntimeException expected) {
            // expected path
        }

        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(1, indicator.getCalculationCount());

        assertNumEquals(endIndex, indicator.getValue(endIndex));
        assertEquals(endIndex, indicator.getHighestResultIndex());
        assertEquals(2, indicator.getCalculationCount());

        indicator.getValue(endIndex);
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void invalidateFromCancelsInFlightLastBarComputation() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        int endIndex = barSeries.getEndIndex();

        BlockingLastBarIndicator indicator = new BlockingLastBarIndicator(barSeries, endIndex);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(endIndex));

            assertTrue("Last-bar calculation did not start in time",
                    indicator.lastBarCalculationStarted.await(30, TimeUnit.SECONDS));

            indicator.invalidateFrom(endIndex);
            indicator.allowLastBarCalculation.countDown();

            future.get(30, TimeUnit.SECONDS);
            assertEquals(1, indicator.getCalculationCount());

            // The in-flight computation must not repopulate the last-bar cache after
            // invalidation.
            assertEquals(-1, indicator.getHighestResultIndex());

            // Next read should recompute and then cache.
            indicator.getValue(endIndex);
            assertEquals(2, indicator.getCalculationCount());
            assertEquals(endIndex, indicator.getHighestResultIndex());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void highestResultIndexUpdatedWhenLastBarAccessedFirst() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        TestIndicator indicator = new TestIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();
        assertEquals(-1, indicator.getHighestResultIndex());

        // Access last bar first - should update highestResultIndex
        Num value = indicator.getValue(endIndex);
        assertNumEquals(endIndex, value);
        assertEquals("highestResultIndex should be updated when last bar is accessed first", endIndex,
                indicator.getHighestResultIndex());
    }

    @Test
    public void highestResultIndexNotDecreasedWhenEarlierIndexAccessedAfterLastBar() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        TestIndicator indicator = new TestIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Access last bar first - sets highestResultIndex to endIndex
        indicator.getValue(endIndex);
        assertEquals("highestResultIndex should be set to endIndex", endIndex, indicator.getHighestResultIndex());

        // Access an earlier index - should NOT decrease highestResultIndex
        // The cache's highestResultIndex might be smaller, but we should take the max
        int earlierIndex = 1;
        indicator.getValue(earlierIndex);

        // highestResultIndex should remain at endIndex (or higher), not decrease
        assertTrue("highestResultIndex should not decrease when accessing earlier index after last bar",
                indicator.getHighestResultIndex() >= endIndex);
    }

    @Test
    public void highestResultIndexNotDecreasedWhenInvalidateFromDoesNotAffectLastBarCache() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        TestIndicator indicator = new TestIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Access last bar first - sets highestResultIndex via last-bar cache
        indicator.getValue(endIndex);
        assertEquals(endIndex, indicator.getHighestResultIndex());

        // Invalidate from an index that does not affect the cached last-bar index.
        indicator.invalidateFrom(endIndex + 1);

        assertEquals(
                "highestResultIndex should remain at least at the last-bar cached index when last-bar cache remains valid",
                endIndex, indicator.getHighestResultIndex());
    }

    @Test
    public void invalidateFromDoesNotClearLastBarCacheWhenNotAffected() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Cache last bar
        indicator.getValue(endIndex);
        assertEquals(1, indicator.getCalculationCount());

        // Invalidate beyond endIndex; last-bar cache should remain valid
        indicator.invalidateFrom(endIndex + 1);

        // No recomputation expected
        indicator.getValue(endIndex);
        assertEquals(1, indicator.getCalculationCount());
    }

    @Test
    public void invalidateFromClearsLastBarCacheWhenAffected() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Cache last bar
        indicator.getValue(endIndex);
        assertEquals(1, indicator.getCalculationCount());

        // Invalidate from endIndex; this must clear the last-bar cache
        indicator.invalidateFrom(endIndex);

        // Next read of the last bar must recompute
        indicator.getValue(endIndex);
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void invalidateCacheClearsAllValues() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(1);

        // Cached result should be reused for the same index.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(1);

        indicator.invalidateCache();

        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(2));
        assertThat(indicator.getCalculationCount()).isEqualTo(2);
    }

    @Test
    public void invalidateFromClearsTailOnly() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        indicator.getValue(1);
        indicator.getValue(2);
        assertThat(indicator.getCalculationCount()).isEqualTo(3);

        indicator.invalidateFrom(1);

        // Index 0 stays cached.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(3);

        // Indices 1 and 2 are recomputed.
        assertThat(indicator.getValue(1)).isEqualByComparingTo(numFactory.numOf(4));
        assertThat(indicator.getValue(2)).isEqualByComparingTo(numFactory.numOf(5));
        assertThat(indicator.getCalculationCount()).isEqualTo(5);
    }

    @Test
    public void invalidateFromNegativeClearsAll() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        assertThat(indicator.getCalculationCount()).isEqualTo(1);

        indicator.invalidateFrom(-1);

        // Cache should be fully cleared.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(2));
        assertThat(indicator.getCalculationCount()).isEqualTo(2);
    }

    @Test
    public void invalidateFromBeyondHighestIsNoOp() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        indicator.getValue(1);
        assertThat(indicator.getCalculationCount()).isEqualTo(2);

        indicator.invalidateFrom(5);

        // Cached values remain intact.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(2);
    }

    @Test
    public void invalidateFromAtFirstCachedClearsAll() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        indicator.getValue(1);
        assertThat(indicator.getCalculationCount()).isEqualTo(2);

        indicator.invalidateFrom(0);

        // All cached values should be dropped.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(3));
        assertThat(indicator.getCalculationCount()).isEqualTo(3);
    }

    @Test
    public void invalidateFromOnEmptyCacheIsSafe() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.invalidateFrom(1);

        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(1);
    }

    @Test
    public void evictionWithSmallMaximumBarCountAndWrapAround() {
        // Test the O(1) eviction with a small maximumBarCount (3) and >10 bars
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .build();
        barSeries.setMaximumBarCount(3);

        CountingIndicator indicator = new CountingIndicator(barSeries);

        int startIndex = barSeries.getBeginIndex();
        int endIndex = barSeries.getEndIndex();
        for (int i = startIndex; i <= endIndex; i++) {
            Num value = indicator.getValue(i);
            assertNumEquals(i, value);
        }

        // Each cached index should be computed exactly once
        assertEquals(endIndex - startIndex + 1, indicator.getCalculationCount());

        // Reset counter to verify cache hits
        indicator.resetCalculationCount();

        // Access the remaining cached values (10, 11, 12) - should be cache hits
        for (int i = startIndex; i <= endIndex; i++) {
            assertNumEquals(i, indicator.getValue(i));
        }

        // No new calculations should have occurred for cached values
        assertEquals(0, indicator.getCalculationCount());
    }

    @Test
    public void lastBarCacheReusesValueWhenUnchanged() {
        // Create a series with mutable last bar
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // First access to last bar should compute
        Num firstValue = indicator.getValue(endIndex);
        assertNumEquals(endIndex, firstValue);
        assertEquals(1, indicator.getCalculationCount());

        // Repeated access without bar mutation should reuse cached value
        Num secondValue = indicator.getValue(endIndex);
        assertNumEquals(endIndex, secondValue);
        assertEquals(1, indicator.getCalculationCount()); // No new computation
    }

    @Test
    public void lastBarCacheInvalidatesOnMutation() {
        // Create a series with mutable last bar
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();

        // Use an indicator that returns the close price to verify mutation detection
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 2);

        int endIndex = barSeries.getEndIndex();

        // First access: SMA of (2, 3) = 2.5
        Num firstValue = sma.getValue(endIndex);
        assertNumEquals(2.5, firstValue);

        // Mutate the last bar
        barSeries.getLastBar().addTrade(numOf(1), numOf(10)); // Close price changes to 10

        // Second access should detect mutation and recompute
        // SMA of (2, 10) = 6.0
        Num secondValue = sma.getValue(endIndex);
        assertNumEquals(6.0, secondValue);
    }

    @Test
    public void lastBarCacheInvalidatesOnReplace() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        ClosePriceCountingIndicator indicator = new ClosePriceCountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        assertNumEquals(3, indicator.getValue(endIndex));
        assertEquals(1, indicator.getCalculationCount());

        assertNumEquals(3, indicator.getValue(endIndex));
        assertEquals(1, indicator.getCalculationCount());

        barSeries.addBar(barSeries.barBuilder()
                .closePrice(10)
                .openPrice(10)
                .highPrice(10)
                .lowPrice(10)
                .volume(0)
                .amount(0)
                .trades(0)
                .build(), true);

        assertNumEquals(10, indicator.getValue(endIndex));
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void recursiveCalculateDoesNotDeadlock() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        SelfReferencingIndicator indicator = new SelfReferencingIndicator(barSeries);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(4));
            Num result = future.get(2, TimeUnit.SECONDS);
            assertNumEquals(5, result);
            assertEquals(5, indicator.getCalculationCount());
        } catch (TimeoutException e) {
            fail("getValue should not deadlock for recursive indicators");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void highestResultIndexNotAdvancedWhenLastBarAccessedRecursivelyWhileHoldingWriteLock() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();

        // Indicator that reads endIndex (last bar) from within calculate(endIndex-1)
        // while holding the cache write lock
        RecursiveLastBarAccessIndicator indicator = new RecursiveLastBarAccessIndicator(barSeries, endIndex);

        // Access a non-last bar to trigger the recursive last-bar access
        Num value = indicator.getValue(endIndex - 1);
        assertNumEquals(endIndex - 1, value);

        // The recursive last-bar access should NOT advance highestResultIndex because
        // snapshotInvalidationCount is -1 when the write lock is already held.
        // Only the outer calculation (endIndex - 1) should advance it.
        assertEquals("highestResultIndex should only reflect the outer calculation, not the recursive last-bar access",
                endIndex - 1, indicator.getHighestResultIndex());

        // Accessing the last bar normally should now update highestResultIndex
        indicator.getValue(endIndex);
        assertEquals("highestResultIndex should be updated after normal last-bar access", endIndex,
                indicator.getHighestResultIndex());
    }

    @Test
    public void lastBarWaitTimeoutDoesNotCauseIndefiniteBlock() throws Exception {
        // Test that a stuck last-bar computation doesn't block other threads
        // indefinitely. After the timeout, other threads should compute independently.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();

        // Create an indicator that blocks forever in its first last-bar calculation
        CountDownLatch firstComputationStarted = new CountDownLatch(1);
        CountDownLatch blockForever = new CountDownLatch(1); // Never counted down

        NeverFinishingIndicator indicator = new NeverFinishingIndicator(barSeries, endIndex, firstComputationStarted,
                blockForever);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Start first thread that will block forever
            executor.submit(() -> {
                try {
                    indicator.getValue(endIndex);
                } catch (Exception e) {
                    // Expected to eventually be interrupted
                }
            });

            // Wait for first computation to start
            assertTrue("First computation should start", firstComputationStarted.await(30, TimeUnit.SECONDS));

            // Give some time for the computation to be "in progress"
            Thread.sleep(100);

            // Start second thread that should timeout waiting and compute independently
            Future<Num> secondFuture = executor.submit(() -> indicator.getValue(endIndex));

            // The second thread should complete within a reasonable time (timeout +
            // computation)
            // even though the first thread is blocked forever
            try {
                Num result = secondFuture.get(15, TimeUnit.SECONDS);
                // Either gets a computed value or times out waiting - both are acceptable
                // The key is that it doesn't block forever
                assertNotNull("Second thread should get a result after timeout", result);
            } catch (TimeoutException e) {
                fail("Second thread should not block forever waiting for first computation");
            }

        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void stressTestConcurrentLastBarAccess() throws InterruptedException {
        // Stress test: multiple threads concurrently accessing the last bar.
        // Moderate concurrency is sufficient to expose race conditions.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = new CountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        int threads = 8;
        int iterationsPerThread = 100;
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        Num value = indicator.getValue(endIndex);
                        if (value != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Stress test did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        // All reads should succeed
        assertEquals("All reads should succeed", threads * iterationsPerThread, successCount.get());

        // Last bar should only be computed once (subsequent reads use cache)
        assertEquals("Last bar should be computed exactly once", 1, indicator.getCalculationCount());
    }

    @Test
    public void stressTestConcurrentLastBarWithMutations() throws InterruptedException {
        // Stress test: concurrent reads with periodic mutations.
        // Moderate iteration count catches race conditions without excessive runtime.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        ClosePriceCountingIndicator indicator = new ClosePriceCountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        int readers = 8;
        int iterations = 50;
        AtomicInteger totalReads = new AtomicInteger(0);
        AtomicBoolean mutationsDone = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch ready = new CountDownLatch(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers + 1);

        // Reader threads
        for (int r = 0; r < readers; r++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    while (!mutationsDone.get()) {
                        indicator.getValue(endIndex);
                        totalReads.incrementAndGet();
                        Thread.yield();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // Mutator thread
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    barSeries.getLastBar().addTrade(numOf(1), numOf(i + 10));
                    Thread.sleep(1);
                }
                mutationsDone.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        ready.await();
        start.countDown();
        assertTrue("Stress test with mutations did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Should have performed many reads
        assertTrue("Should have performed many concurrent reads", totalReads.get() > 100);

        // Each mutation should trigger a recomputation
        assertTrue("Should have recomputed after mutations", indicator.getCalculationCount() > 1);
    }

    private final static class CountingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();

        private CountingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }

        private void resetCalculationCount() {
            calculations.set(0);
        }
    }

    private final static class ClosePriceCountingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();

        private ClosePriceCountingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }
    }

    private final static class FailingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();
        private final AtomicBoolean failFirst = new AtomicBoolean(true);
        private final int failIndex;

        private FailingIndicator(BarSeries series, int failIndex) {
            super(series);
            this.failIndex = failIndex;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            if (index == failIndex && failFirst.compareAndSet(true, false)) {
                throw new RuntimeException("boom");
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }

        private int getHighestResultIndex() {
            return highestResultIndex;
        }

        private int getCacheHighestResultIndex() {
            return getCache().getHighestResultIndex();
        }
    }

    private final static class BlockingLastBarIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();
        private final CountDownLatch lastBarCalculationStarted = new CountDownLatch(1);
        private final CountDownLatch allowLastBarCalculation = new CountDownLatch(1);
        private final int endIndex;

        private BlockingLastBarIndicator(BarSeries series, int endIndex) {
            super(series);
            this.endIndex = endIndex;
        }

        @Override
        protected Num calculate(int index) {
            int count = calculations.incrementAndGet();
            if (index == endIndex) {
                lastBarCalculationStarted.countDown();
                try {
                    assertTrue("Last-bar calculation was not allowed to proceed in time",
                            allowLastBarCalculation.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return getBarSeries().numFactory().numOf(count);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }

        private int getHighestResultIndex() {
            return highestResultIndex;
        }
    }

    private final static class WriteLockedLastBarIndicator extends CachedIndicator<Num> {

        private final AtomicBoolean writeLockedDuringLastBarRead = new AtomicBoolean();
        private final CountDownLatch lastBarCalculationStarted = new CountDownLatch(1);
        private final CountDownLatch writeLockedCalculationStarted = new CountDownLatch(1);
        private final int endIndex;

        private WriteLockedLastBarIndicator(BarSeries series, int endIndex) {
            super(series);
            this.endIndex = endIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == endIndex) {
                lastBarCalculationStarted.countDown();
                try {
                    assertTrue("Write-locked calculation did not start in time",
                            writeLockedCalculationStarted.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return getValue(0).plus(getBarSeries().numFactory().numOf(index));
            }

            if (index == endIndex - 1) {
                writeLockedCalculationStarted.countDown();
                try {
                    assertTrue("Last-bar calculation did not start in time",
                            lastBarCalculationStarted.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                writeLockedDuringLastBarRead.set(getCache().isWriteLockedByCurrentThread());
                assertNotNull(getValue(endIndex));
                return getBarSeries().numFactory().numOf(index);
            }

            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private final static class CountingInvalidatableIndicator extends CachedIndicator<Num> {

        private int calculationCount = 0;

        private CountingInvalidatableIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculationCount++;
            return getBarSeries().numFactory().numOf(calculationCount);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getCalculationCount() {
            return calculationCount;
        }
    }

    private final static class SelfReferencingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculationCount = new AtomicInteger();

        private SelfReferencingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculationCount.incrementAndGet();
            if (index == 0) {
                return getBarSeries().numFactory().one();
            }
            return getValue(index - 1).plus(getBarSeries().numFactory().one());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getCalculationCount() {
            return calculationCount.get();
        }
    }

    private final static class TestIndicator extends CachedIndicator<Num> {

        private TestIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getHighestResultIndex() {
            return highestResultIndex;
        }
    }

    private final static class RecursiveLastBarAccessIndicator extends CachedIndicator<Num> {

        private final int endIndex;

        private RecursiveLastBarAccessIndicator(BarSeries series, int endIndex) {
            super(series);
            this.endIndex = endIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == endIndex - 1) {
                // While holding the cache write lock for (endIndex - 1), access the last bar.
                // This triggers the code path where isWriteLockedByCurrentThread() returns
                // true, causing snapshotInvalidationCount = -1.
                getValue(endIndex);
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getHighestResultIndex() {
            return highestResultIndex;
        }
    }

    private final static class BlockingTradesBar implements Bar {

        private final AtomicBoolean blockingEnabled = new AtomicBoolean();
        private final AtomicBoolean blocked = new AtomicBoolean();
        private final Bar delegate;
        private final CountDownLatch tradesReadStarted;
        private final CountDownLatch allowTradesRead;

        private BlockingTradesBar(Bar delegate, CountDownLatch tradesReadStarted, CountDownLatch allowTradesRead) {
            this.delegate = delegate;
            this.tradesReadStarted = tradesReadStarted;
            this.allowTradesRead = allowTradesRead;
        }

        private void enableBlocking() {
            blockingEnabled.set(true);
        }

        @Override
        public Duration getTimePeriod() {
            return delegate.getTimePeriod();
        }

        @Override
        public Instant getBeginTime() {
            return delegate.getBeginTime();
        }

        @Override
        public Instant getEndTime() {
            return delegate.getEndTime();
        }

        @Override
        public Num getOpenPrice() {
            return delegate.getOpenPrice();
        }

        @Override
        public Num getHighPrice() {
            return delegate.getHighPrice();
        }

        @Override
        public Num getLowPrice() {
            return delegate.getLowPrice();
        }

        @Override
        public Num getClosePrice() {
            return delegate.getClosePrice();
        }

        @Override
        public Num getVolume() {
            return delegate.getVolume();
        }

        @Override
        public Num getAmount() {
            return delegate.getAmount();
        }

        @Override
        public long getTrades() {
            if (blockingEnabled.get() && blocked.compareAndSet(false, true)) {
                tradesReadStarted.countDown();
                try {
                    assertTrue("Timed out waiting to allow getTrades to proceed",
                            allowTradesRead.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.getTrades();
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            delegate.addTrade(tradeVolume, tradePrice);
        }

        @Override
        public void addPrice(Num price) {
            delegate.addPrice(price);
        }
    }

    private static final class NeverFinishingIndicator extends CachedIndicator<Num> {

        private final CountDownLatch computationStarted;
        private final CountDownLatch blockLatch;
        private final int targetIndex;
        private final AtomicBoolean firstCall = new AtomicBoolean(true);

        private NeverFinishingIndicator(BarSeries series, int targetIndex, CountDownLatch computationStarted,
                CountDownLatch blockLatch) {
            super(series);
            this.targetIndex = targetIndex;
            this.computationStarted = computationStarted;
            this.blockLatch = blockLatch;
        }

        @Override
        protected Num calculate(int index) {
            if (index == targetIndex && firstCall.compareAndSet(true, false)) {
                computationStarted.countDown();
                try {
                    // Block forever (or until interrupted)
                    blockLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

}

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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);

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

        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);
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
    public void lastBarMutationDuringCalculationRetriesBeforeReturning() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        RevisionBlockingIndicator indicator = new RevisionBlockingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(endIndex));
            assertTrue("Last-bar calculation did not start in time",
                    indicator.firstCalculationStarted.await(30, TimeUnit.SECONDS));

            barSeries.addTrade(numOf(1), numOf(10));
            indicator.allowFirstCalculation.countDown();

            assertNumEquals(10, future.get(30, TimeUnit.SECONDS));
            assertEquals(2, indicator.calculations.get());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void previousLastBarPromotionRejectsRevisionAbaMutation() {
        BarSeries barSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();
        Duration period = Duration.ofMinutes(1);
        barSeries.barBuilder()
                .timePeriod(period)
                .endTime(Instant.EPOCH.plus(period))
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(1)
                .add();
        barSeries.barBuilder()
                .timePeriod(period)
                .endTime(Instant.EPOCH.plus(period.multipliedBy(2)))
                .openPrice(10)
                .highPrice(10)
                .lowPrice(10)
                .closePrice(10)
                .add();
        HighPriceIndicator high = new HighPriceIndicator(barSeries);
        SMAIndicator cachedHigh = new SMAIndicator(high, 1);
        int previousLastIndex = barSeries.getEndIndex();
        assertNumEquals(10, cachedHigh.getValue(previousLastIndex));

        barSeries.addPrice(numOf(20));
        barSeries.addPrice(numOf(10));
        barSeries.addBar(barSeries.barBuilder()
                .timePeriod(period)
                .endTime(Instant.EPOCH.plus(period.multipliedBy(3)))
                .openPrice(30)
                .highPrice(30)
                .lowPrice(30)
                .closePrice(30)
                .build());

        assertNumEquals(20, cachedHigh.getValue(previousLastIndex));
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
        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);
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
        long lastBarWaitTimeoutMs = 50;

        // Create an indicator that blocks forever in its first last-bar calculation
        CountDownLatch firstComputationStarted = new CountDownLatch(1);
        CountDownLatch blockForever = new CountDownLatch(1); // Never counted down

        NeverFinishingIndicator indicator = new NeverFinishingIndicator(barSeries, endIndex, firstComputationStarted,
                blockForever, lastBarWaitTimeoutMs);

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

            // Start second thread that should timeout waiting and compute independently
            Future<Num> secondFuture = executor.submit(() -> indicator.getValue(endIndex));

            // The second thread should complete within a reasonable time (timeout +
            // computation)
            // even though the first thread is blocked forever
            try {
                Num result = secondFuture.get(1, TimeUnit.SECONDS);
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
        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);
        int endIndex = barSeries.getEndIndex();

        int readers = 8;
        int iterations = 50;
        int minimumReads = 100;
        AtomicInteger totalReads = new AtomicInteger(0);
        AtomicBoolean mutationsDone = new AtomicBoolean(false);
        CountDownLatch readsObserved = new CountDownLatch(minimumReads);

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
                        int readCount = totalReads.incrementAndGet();
                        if (readCount <= minimumReads) {
                            readsObserved.countDown();
                        }
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
                readsObserved.await(5, TimeUnit.SECONDS);
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
        assertTrue("Should have performed many concurrent reads", totalReads.get() >= minimumReads);

        // Each mutation should trigger a recomputation
        assertTrue("Should have recomputed after mutations", indicator.getCalculationCount() > 1);
    }

    @Test
    public void equivalentOptedInInstancesShareOneCalculation() {
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator first = new SharedCountingIndicator(series, "shared", calculations);
        SharedCountingIndicator second = new SharedCountingIndicator(series, "shared", calculations);

        assertNotSame(first, second);
        assertNumEquals(4, first.getValue(3));
        assertNumEquals(4, second.getValue(3));
        assertEquals(1, calculations.get());
    }

    @Test
    public void sharedLastBarWaiterRefreshesProtectedHighestIndexMirror() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        AtomicInteger calculations = new AtomicInteger();
        CountDownLatch calculationStarted = new CountDownLatch(1);
        CountDownLatch allowCalculation = new CountDownLatch(1);
        SharedBlockingIndicator first = new SharedBlockingIndicator(barSeries, calculations, calculationStarted,
                allowCalculation);
        SharedBlockingIndicator second = new SharedBlockingIndicator(barSeries, calculations, calculationStarted,
                allowCalculation);
        int endIndex = barSeries.getEndIndex();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Thread> waiterThread = new AtomicReference<>();
        CountDownLatch waiterStarted = new CountDownLatch(1);

        try {
            Future<Num> firstResult = executor.submit(() -> first.getValue(endIndex));
            assertTrue(calculationStarted.await(30, TimeUnit.SECONDS));
            Future<Num> secondResult = executor.submit(() -> {
                waiterThread.set(Thread.currentThread());
                waiterStarted.countDown();
                return second.getValue(endIndex);
            });
            assertTrue(waiterStarted.await(30, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (waiterThread.get().getState() != Thread.State.TIMED_WAITING && !secondResult.isDone()
                    && System.nanoTime() < deadline) {
                Thread.sleep(1L);
            }
            assertEquals(Thread.State.TIMED_WAITING, waiterThread.get().getState());

            allowCalculation.countDown();
            assertNumEquals(4, firstResult.get(30, TimeUnit.SECONDS));
            assertNumEquals(4, secondResult.get(30, TimeUnit.SECONDS));
            assertEquals(1, calculations.get());
            assertEquals(endIndex, second.highestIndexMirror());
        } finally {
            allowCalculation.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void existingSuperclassConstructorKeepsStatefulIndicatorsIsolated() {
        CountingIndicator first = CountingIndicator.closePrice(series);
        CountingIndicator second = CountingIndicator.closePrice(series);

        first.getValue(3);
        second.getValue(3);
        assertEquals(1, first.getCalculationCount());
        assertEquals(1, second.getCalculationCount());
    }

    @Test
    public void sharedStateDistinguishesParametersClassesSeriesAndOpaqueInputs() {
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator first = new SharedCountingIndicator(series, "first", calculations);
        SharedCountingIndicator differentParameter = new SharedCountingIndicator(series, "second", calculations);
        OtherSharedCountingIndicator differentClass = new OtherSharedCountingIndicator(series, "first", calculations);
        BarSeries otherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        SharedCountingIndicator differentSeries = new SharedCountingIndicator(otherSeries, "first", calculations);

        first.getValue(2);
        differentParameter.getValue(2);
        differentClass.getValue(2);
        differentSeries.getValue(2);
        assertEquals(4, calculations.get());

        Object opaque = new Object();
        SharedCountingIndicator sameOpaqueFirst = new SharedCountingIndicator(series, opaque, calculations);
        SharedCountingIndicator sameOpaqueSecond = new SharedCountingIndicator(series, opaque, calculations);
        SharedCountingIndicator differentOpaque = new SharedCountingIndicator(series, new Object(), calculations);
        sameOpaqueFirst.getValue(4);
        sameOpaqueSecond.getValue(4);
        differentOpaque.getValue(4);
        assertEquals(6, calculations.get());

        List<Integer> mutableConfiguration = new ArrayList<>(List.of(1, 2));
        SharedCountingIndicator snapshotted = new SharedCountingIndicator(series, mutableConfiguration, calculations);
        mutableConfiguration.add(3);
        SharedCountingIndicator equivalentSnapshot = new SharedCountingIndicator(series, new ArrayList<>(List.of(1, 2)),
                calculations);
        snapshotted.getValue(6);
        equivalentSnapshot.getValue(6);
        assertEquals(7, calculations.get());
    }

    @Test
    public void structuralIdentityPreservesContainerTypesAndIterationOrder() {
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator primitiveArray = new SharedCountingIndicator(series, new int[] { 1 }, calculations);
        SharedCountingIndicator boxedArray = new SharedCountingIndicator(series, new Integer[] { 1 }, calculations);
        SharedCountingIndicator list = new SharedCountingIndicator(series, List.of(1), calculations);
        assertNotSame(primitiveArray.sharedStateIdentity(), boxedArray.sharedStateIdentity());
        assertNotSame(primitiveArray.sharedStateIdentity(), list.sharedStateIdentity());

        Map<String, Integer> firstOrder = new LinkedHashMap<>();
        firstOrder.put("first", 1);
        firstOrder.put("second", 2);
        Map<String, Integer> secondOrder = new LinkedHashMap<>();
        secondOrder.put("second", 2);
        secondOrder.put("first", 1);
        SharedCountingIndicator firstMap = new SharedCountingIndicator(series, firstOrder, calculations);
        SharedCountingIndicator secondMap = new SharedCountingIndicator(series, secondOrder, calculations);
        assertNotSame(firstMap.sharedStateIdentity(), secondMap.sharedStateIdentity());
    }

    @Test
    public void mutableConstantValuesRemainOpaqueToDownstreamIdentity() {
        List<Integer> firstValue = new ArrayList<>(List.of(1));
        List<Integer> secondValue = new ArrayList<>(List.of(1));
        ConstantIndicator<List<Integer>> firstConstant = new ConstantIndicator<>(series, firstValue);
        ConstantIndicator<List<Integer>> secondConstant = new ConstantIndicator<>(series, secondValue);
        SharedForwardingIndicator<List<Integer>> first = new SharedForwardingIndicator<>(firstConstant);
        SharedForwardingIndicator<List<Integer>> second = new SharedForwardingIndicator<>(secondConstant);

        assertNotSame(first.sharedStateIdentity(), second.sharedStateIdentity());
        firstValue.add(2);
        assertEquals(List.of(1, 2), first.getValue(3));
        assertEquals(List.of(1), second.getValue(3));
    }

    @Test
    public void sharedStateDistinguishesDecimalArithmeticContexts() {
        NumFactory lowPrecisionFactory = DecimalNumFactory.getInstance(16);
        NumFactory highPrecisionFactory = DecimalNumFactory.getInstance(40);
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(lowPrecisionFactory).withData(1, 1).build();
        Num lowPrecisionValue = lowPrecisionFactory.numOf(2).dividedBy(lowPrecisionFactory.numOf(3));
        Num highPrecisionValue = highPrecisionFactory.numOf(2).dividedBy(highPrecisionFactory.numOf(3));
        SMAIndicator lowPrecision = new SMAIndicator(new ConstantIndicator<>(decimalSeries, lowPrecisionValue), 1);
        SMAIndicator highPrecision = new SMAIndicator(new ConstantIndicator<>(decimalSeries, highPrecisionValue), 1);

        assertNotSame(((CachedIndicator<?>) lowPrecision).sharedStateIdentity(),
                ((CachedIndicator<?>) highPrecision).sharedStateIdentity());
        assertEquals(16, ((DecimalNum) lowPrecision.getValue(1)).getMathContext().getPrecision());
        assertEquals(40, ((DecimalNum) highPrecision.getValue(1)).getMathContext().getPrecision());
    }

    @Test
    public void sharedStateComputesOnceUnderContentionAndCachesNull() throws Exception {
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator first = new SharedCountingIndicator(series, "concurrent", calculations);
        SharedCountingIndicator second = new SharedCountingIndicator(series, "concurrent", calculations);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Num>> futures = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            SharedCountingIndicator target = i % 2 == 0 ? first : second;
            futures.add(executor.submit(() -> {
                start.await();
                return target.getValue(5);
            }));
        }
        start.countDown();
        for (Future<Num> future : futures) {
            assertNumEquals(4, future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        assertEquals(1, calculations.get());

        AtomicInteger nullCalculations = new AtomicInteger();
        SharedNullIndicator nullFirst = new SharedNullIndicator(series, nullCalculations);
        SharedNullIndicator nullSecond = new SharedNullIndicator(series, nullCalculations);
        assertNull(nullFirst.getValue(4));
        assertNull(nullSecond.getValue(4));
        assertEquals(1, nullCalculations.get());
    }

    @Test
    public void failedSharedCalculationIsRetriedByEquivalentInstance() {
        AtomicInteger calculations = new AtomicInteger();
        AtomicBoolean failFirst = new AtomicBoolean(true);
        SharedFailingIndicator first = new SharedFailingIndicator(series, calculations, failFirst);
        SharedFailingIndicator second = new SharedFailingIndicator(series, calculations, failFirst);

        assertThrows(IllegalStateException.class, () -> first.getValue(4));
        assertNumEquals(4, second.getValue(4));
        assertEquals(2, calculations.get());
    }

    @Test
    public void historyEpochInvalidatesSharedStateButAppendAndPruningPreserveIt() {
        BaseBarSeries mutableSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5)
                .build();
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator first = new SharedCountingIndicator(mutableSeries, "epoch", calculations);
        SharedCountingIndicator second = new SharedCountingIndicator(mutableSeries, "epoch", calculations);

        first.getValue(2);
        mutableSeries.addBar(mutableSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(mutableSeries.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .closePrice(6)
                .build());
        second.getValue(2);
        assertEquals(1, calculations.get());

        mutableSeries.replaceBar(1, mutableSeries.getBar(1));
        second.getValue(2);
        assertEquals(2, calculations.get());

        List<Bar> bars = mutableSeries.getBarData();
        mutableSeries.clear();
        bars.forEach(mutableSeries::addBar);
        first.getValue(2);
        assertEquals(3, calculations.get());

        first.getValue(4);
        assertEquals(4, calculations.get());
        mutableSeries.setMaximumBarCount(3);
        second.getValue(4);
        assertEquals(4, calculations.get());
    }

    @Test
    public void liveBarMutationInvalidatesOneSharedLastBarValue() {
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator first = new SharedCountingIndicator(series, "live", calculations);
        SharedCountingIndicator second = new SharedCountingIndicator(series, "live", calculations);
        int endIndex = series.getEndIndex();

        first.getValue(endIndex);
        second.getValue(endIndex);
        assertEquals(1, calculations.get());

        series.addPrice(numFactory.numOf(42));
        assertNumEquals(42, second.getValue(endIndex));
        assertEquals(2, calculations.get());
    }

    @Test
    public void appendPromotesSharedLastBarWithoutRecalculation() {
        BaseBarSeries mutableSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5)
                .build();
        AtomicInteger calculations = new AtomicInteger();
        SharedCountingIndicator first = new SharedCountingIndicator(mutableSeries, "append", calculations);
        SharedCountingIndicator second = new SharedCountingIndicator(mutableSeries, "append", calculations);
        int previousEndIndex = mutableSeries.getEndIndex();

        first.getValue(previousEndIndex);
        Bar lastBar = mutableSeries.getLastBar();
        mutableSeries.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime().plus(lastBar.getTimePeriod()))
                .closePrice(6)
                .add();

        assertNumEquals(5, second.getValue(previousEndIndex));
        assertEquals(1, calculations.get());
    }

    @Test
    public void historicalMutationCannotRaceWithLastBarPromotion() throws Exception {
        BaseBarSeries source = (BaseBarSeries) new MockBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4, 5)
                .build();
        EpochBlockingSeries mutableSeries = new EpochBlockingSeries(source.getBarData());
        SMAIndicator first = new SMAIndicator(new ClosePriceIndicator(mutableSeries), 5);
        SMAIndicator second = new SMAIndicator(new ClosePriceIndicator(mutableSeries), 5);
        int previousEndIndex = mutableSeries.getEndIndex();

        assertNumEquals(3, first.getValue(previousEndIndex));
        Bar lastBar = mutableSeries.getLastBar();
        mutableSeries.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime().plus(lastBar.getTimePeriod()))
                .closePrice(6)
                .add();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        mutableSeries.blockNextEpochRead();
        Future<Num> result = executor.submit(() -> second.getValue(previousEndIndex));
        try {
            assertTrue(mutableSeries.awaitBlockedEpochRead());
            Bar firstBar = mutableSeries.getFirstBar();
            Bar replacement = mutableSeries.barBuilder()
                    .timePeriod(firstBar.getTimePeriod())
                    .endTime(firstBar.getEndTime())
                    .closePrice(101)
                    .build();
            mutableSeries.replaceBar(0, replacement);
            mutableSeries.allowEpochRead();

            assertNumEquals(23, result.get(10, TimeUnit.SECONDS));
        } finally {
            mutableSeries.allowEpochRead();
            executor.shutdownNow();
        }
    }

    @Test
    public void historicalMutationDuringCalculationCannotPublishStaleSharedState() throws Exception {
        BaseBarSeries mutableSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5)
                .build();
        CountDownLatch calculationStarted = new CountDownLatch(1);
        CountDownLatch calculationMayFinish = new CountDownLatch(1);
        AtomicInteger calculations = new AtomicInteger();
        EpochBlockingIndicator first = new EpochBlockingIndicator(mutableSeries, calculations, calculationStarted,
                calculationMayFinish);
        EpochBlockingIndicator second = new EpochBlockingIndicator(mutableSeries, calculations, calculationStarted,
                calculationMayFinish);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Num> result = executor.submit(() -> first.getValue(3));

        assertTrue(calculationStarted.await(10, TimeUnit.SECONDS));
        mutableSeries.replaceBar(1, mutableSeries.getBar(1));
        calculationMayFinish.countDown();

        assertNumEquals(4, result.get(10, TimeUnit.SECONDS));
        assertNumEquals(4, second.getValue(3));
        executor.shutdownNow();
        assertEquals(2, calculations.get());
    }

    @Test
    public void historicalMutationDuringRecursivePrefillRetriesOnNewEpoch() throws Exception {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            values.add(1d);
        }
        BaseBarSeries mutableSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(values)
                .build();
        CountDownLatch calculationStarted = new CountDownLatch(1);
        CountDownLatch calculationMayFinish = new CountDownLatch(1);
        PrefillEpochBlockingIndicator first = new PrefillEpochBlockingIndicator(mutableSeries, calculationStarted,
                calculationMayFinish);
        PrefillEpochBlockingIndicator second = new PrefillEpochBlockingIndicator(mutableSeries, calculationStarted,
                calculationMayFinish);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Num> accumulated = executor.submit(() -> first.getValue(150));

        try {
            assertTrue(calculationStarted.await(10, TimeUnit.SECONDS));
            Bar firstBar = mutableSeries.getFirstBar();
            Bar replacement = mutableSeries.barBuilder()
                    .timePeriod(firstBar.getTimePeriod())
                    .endTime(firstBar.getEndTime())
                    .closePrice(100)
                    .build();
            mutableSeries.replaceBar(0, replacement);
            Future<Num> firstValue = executor.submit(() -> second.getValue(0));
            calculationMayFinish.countDown();

            assertNumEquals(250, accumulated.get(10, TimeUnit.SECONDS));
            assertNumEquals(100, firstValue.get(10, TimeUnit.SECONDS));
        } finally {
            calculationMayFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void ordinaryConstructorsOfAuditedIndicatorsShareWithoutSharingIdentity() {
        SMAIndicator first = new SMAIndicator(new ClosePriceIndicator(series), 3);
        SMAIndicator second = new SMAIndicator(new ClosePriceIndicator(series), 3);

        assertNotSame(first, second);
        assertSame(((CachedIndicator<?>) first).sharedStateIdentity(),
                ((CachedIndicator<?>) second).sharedStateIdentity());
        assertNumEquals(first.getValue(5), second.getValue(5));
    }

    @Test
    public void subclassesOfAuditedIndicatorsRemainIsolatedUntilTheyOptIn() {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ExtendedSmaIndicator first = new ExtendedSmaIndicator(close, 3, 1);
        ExtendedSmaIndicator second = new ExtendedSmaIndicator(close, 3, 2);

        assertNotSame(((CachedIndicator<?>) first).sharedStateIdentity(),
                ((CachedIndicator<?>) second).sharedStateIdentity());
        assertNumEquals(4, first.getValue(3));
        assertNumEquals(5, second.getValue(3));
    }

    @Test
    public void equivalentFluentSourceGraphsShareTheirCachedComposition() {
        NumericIndicator first = NumericIndicator.closePrice(series).plus(1).sma(3);
        NumericIndicator second = NumericIndicator.closePrice(series).plus(1).sma(3);
        CachedIndicator<?> firstDelegate = (CachedIndicator<?>) first.delegate();
        CachedIndicator<?> secondDelegate = (CachedIndicator<?>) second.delegate();

        assertSame(firstDelegate.sharedStateIdentity(), secondDelegate.sharedStateIdentity());
        assertNumEquals(first.getValue(5), second.getValue(5));
    }

    private static final class ExtendedSmaIndicator extends SMAIndicator {

        private final int adjustment;

        private ExtendedSmaIndicator(Indicator<Num> indicator, int barCount, int adjustment) {
            super(indicator, barCount);
            this.adjustment = adjustment;
        }

        @Override
        protected Num calculate(int index) {
            return super.calculate(index).plus(getBarSeries().numFactory().numOf(adjustment));
        }
    }

    private static final class SharedCountingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations;

        private SharedCountingIndicator(BarSeries series, Object configuration, AtomicInteger calculations) {
            super(series, identityOf(configuration));
            this.calculations = calculations;
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
    }

    private static final class OtherSharedCountingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations;

        private OtherSharedCountingIndicator(BarSeries series, Object configuration, AtomicInteger calculations) {
            super(series, identityOf(configuration));
            this.calculations = calculations;
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
    }

    private static final class SharedBlockingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations;
        private final CountDownLatch calculationStarted;
        private final CountDownLatch allowCalculation;

        private SharedBlockingIndicator(BarSeries series, AtomicInteger calculations, CountDownLatch calculationStarted,
                CountDownLatch allowCalculation) {
            super(series, identityOf("shared-blocking"));
            this.calculations = calculations;
            this.calculationStarted = calculationStarted;
            this.allowCalculation = allowCalculation;
        }

        @Override
        protected Num calculate(int index) {
            if (calculations.incrementAndGet() == 1) {
                calculationStarted.countDown();
                try {
                    assertTrue(allowCalculation.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int highestIndexMirror() {
            return highestResultIndex;
        }
    }

    private static final class SharedNullIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations;

        private SharedNullIndicator(BarSeries series, AtomicInteger calculations) {
            super(series, identityOf("null"));
            this.calculations = calculations;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            return null;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class SharedForwardingIndicator<T> extends CachedIndicator<T> {

        private final Indicator<T> source;

        private SharedForwardingIndicator(Indicator<T> source) {
            super(source, identityOf(source));
            this.source = source;
        }

        @Override
        protected T calculate(int index) {
            return source.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return source.getCountOfUnstableBars();
        }
    }

    private static final class SharedFailingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations;
        private final AtomicBoolean failFirst;

        private SharedFailingIndicator(BarSeries series, AtomicInteger calculations, AtomicBoolean failFirst) {
            super(series, identityOf("failure"));
            this.calculations = calculations;
            this.failFirst = failFirst;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            if (failFirst.compareAndSet(true, false)) {
                throw new IllegalStateException("expected");
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class EpochBlockingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations;
        private final CountDownLatch calculationStarted;
        private final CountDownLatch calculationMayFinish;

        private EpochBlockingIndicator(BarSeries series, AtomicInteger calculations, CountDownLatch calculationStarted,
                CountDownLatch calculationMayFinish) {
            super(series, identityOf("epoch-race"));
            this.calculations = calculations;
            this.calculationStarted = calculationStarted;
            this.calculationMayFinish = calculationMayFinish;
        }

        @Override
        protected Num calculate(int index) {
            if (calculations.incrementAndGet() == 1) {
                calculationStarted.countDown();
                try {
                    assertTrue(calculationMayFinish.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class PrefillEpochBlockingIndicator extends RecursiveCachedIndicator<Num> {

        private final ClosePriceIndicator closePrice;
        private final CountDownLatch calculationStarted;
        private final CountDownLatch calculationMayFinish;
        private final AtomicBoolean blockOnce = new AtomicBoolean(true);

        private PrefillEpochBlockingIndicator(BarSeries series, CountDownLatch calculationStarted,
                CountDownLatch calculationMayFinish) {
            super(series, identityOf("prefill-epoch-race"));
            this.closePrice = new ClosePriceIndicator(series);
            this.calculationStarted = calculationStarted;
            this.calculationMayFinish = calculationMayFinish;
        }

        @Override
        protected Num calculate(int index) {
            if (index == 50 && blockOnce.compareAndSet(true, false)) {
                calculationStarted.countDown();
                try {
                    assertTrue(calculationMayFinish.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            Num current = closePrice.getValue(index);
            return index == 0 ? current : getValue(index - 1).plus(current);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
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

    private static final class RevisionBlockingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();
        private final CountDownLatch firstCalculationStarted = new CountDownLatch(1);
        private final CountDownLatch allowFirstCalculation = new CountDownLatch(1);

        private RevisionBlockingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            Num value = getBarSeries().getBar(index).getClosePrice();
            if (calculations.incrementAndGet() == 1) {
                firstCalculationStarted.countDown();
                try {
                    assertTrue("First calculation was not allowed to proceed in time",
                            allowFirstCalculation.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return value;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
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

    private static final class EpochBlockingSeries extends BaseBarSeries {

        private final AtomicBoolean blockNextEpochRead = new AtomicBoolean();
        private final CountDownLatch epochReadStarted = new CountDownLatch(1);
        private final CountDownLatch allowEpochRead = new CountDownLatch(1);

        private EpochBlockingSeries(List<Bar> bars) {
            super("epoch-blocking", bars);
        }

        private void blockNextEpochRead() {
            blockNextEpochRead.set(true);
        }

        private boolean awaitBlockedEpochRead() throws InterruptedException {
            return epochReadStarted.await(10, TimeUnit.SECONDS);
        }

        private void allowEpochRead() {
            allowEpochRead.countDown();
        }

        @Override
        public long getBarHistoryEpoch() {
            long epoch = super.getBarHistoryEpoch();
            if (blockNextEpochRead.compareAndSet(true, false)) {
                epochReadStarted.countDown();
                try {
                    assertTrue(allowEpochRead.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return epoch;
        }
    }

    private static final class NeverFinishingIndicator extends CachedIndicator<Num> {

        private final CountDownLatch computationStarted;
        private final CountDownLatch blockLatch;
        private final int targetIndex;
        private final AtomicBoolean firstCall = new AtomicBoolean(true);

        private NeverFinishingIndicator(BarSeries series, int targetIndex, CountDownLatch computationStarted,
                CountDownLatch blockLatch, long lastBarWaitTimeoutMs) {
            super(series, lastBarWaitTimeoutMs);
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

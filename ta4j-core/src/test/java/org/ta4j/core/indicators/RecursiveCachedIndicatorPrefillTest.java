/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.ZLEMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RecursiveCachedIndicatorPrefillTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int TARGET_INDEX = 256;

    private BarSeries series;

    public RecursiveCachedIndicatorPrefillTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        double[] data = new double[TARGET_INDEX + 10];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    @Test
    public void prefillGuardPreventsRecursiveOverflow() {
        ReentrantIndicator indicator = new ReentrantIndicator(series, TARGET_INDEX);
        Num value = indicator.getValue(TARGET_INDEX);
        assertNotNull(value);

        LegacyReentrantIndicator legacy = new LegacyReentrantIndicator(series, TARGET_INDEX);
        assertThrows(StackOverflowError.class, () -> legacy.getValue(TARGET_INDEX));
    }

    @Test
    public void largeGapRequestDoesNotCauseStackOverflow() {
        // Create a series with 500 bars
        double[] data = new double[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = i + 1;
        }
        BarSeries largeSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();

        // ZLEMA is a recursive indicator
        ZLEMAIndicator zlema = new ZLEMAIndicator(new ClosePriceIndicator(largeSeries), 20);

        // Request value at the end - should use iterative prefill
        try {
            Num value = zlema.getValue(499);
            assertNotNull(value);
        } catch (StackOverflowError e) {
            fail("Recursive indicator should not cause stack overflow with large gap");
        }
    }

    @Test
    public void iterativePrefillComputesValuesCorrectly() {
        // Create a simple recursive indicator that adds previous value
        double[] data = new double[300];
        for (int i = 0; i < data.length; i++) {
            data[i] = 1; // All values are 1
        }
        BarSeries testSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();

        SumIndicator sumIndicator = new SumIndicator(testSeries);

        // Request value at index 200 - should trigger iterative prefill
        Num value = sumIndicator.getValue(200);

        // Each value should be index + 1 (since sum starts at 1)
        assertEquals(numFactory.numOf(201), value);

        // Verify intermediate values were computed correctly
        assertEquals(numFactory.numOf(1), sumIndicator.getValue(0));
        assertEquals(numFactory.numOf(101), sumIndicator.getValue(100));
        assertEquals(numFactory.numOf(151), sumIndicator.getValue(150));
    }

    @Test
    public void prefillOnlyComputesOncePerIndex() {
        double[] data = new double[300];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        BarSeries testSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();

        CountingRecursiveIndicator indicator = new CountingRecursiveIndicator(testSeries);

        // First request triggers prefill
        indicator.getValue(250);

        // Get the computation count
        int firstCount = indicator.getComputationCount();

        // Subsequent requests should use cache
        indicator.getValue(100);
        indicator.getValue(150);
        indicator.getValue(200);
        indicator.getValue(250);

        // No additional computations should occur
        assertEquals(firstCount, indicator.getComputationCount());
    }

    @Test
    public void highestResultIndexSynchronizedAfterPrefill() {
        // Create a series with a large gap to trigger prefill
        double[] data = new double[300];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        BarSeries testSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();

        TestRecursiveIndicator indicator = new TestRecursiveIndicator(testSeries);

        // Initially both should be -1
        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(-1, indicator.getCacheHighestResultIndex());

        // Request value at index 200 - should trigger prefill (gap > 100)
        indicator.getValue(200);

        // After prefill, indicator's highestResultIndex should be synchronized with
        // cache
        int indicatorHighest = indicator.getHighestResultIndex();
        int cacheHighest = indicator.getCacheHighestResultIndex();
        assertEquals("highestResultIndex should be synchronized from cache after prefill", cacheHighest,
                indicatorHighest);
        assertTrue("highestResultIndex should be >= 200 after prefill", indicatorHighest >= 200);

        // Request another value that requires prefill
        indicator.getValue(250);

        // Should still be synchronized
        assertEquals("highestResultIndex should remain synchronized after second prefill",
                indicator.getCacheHighestResultIndex(), indicator.getHighestResultIndex());
    }

    @Test
    public void highestResultIndexUpdatedWhenLastBarAccessedFirst() {
        // Create a series where accessing last bar first should update
        // highestResultIndex
        double[] data = new double[150];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        BarSeries testSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();

        TestRecursiveIndicator indicator = new TestRecursiveIndicator(testSeries);

        // Initially both should be -1
        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(-1, indicator.getCacheHighestResultIndex());

        int endIndex = testSeries.getEndIndex();

        // Access last bar first - should update highestResultIndex
        // This is the key fix: getLastBarValue() now updates highestResultIndex
        Num value = indicator.getValue(endIndex);
        assertNotNull(value);

        // highestResultIndex should be updated to endIndex when last bar is accessed
        // first
        // This ensures RecursiveCachedIndicator prefilling logic works correctly
        assertEquals("highestResultIndex should be updated when last bar is accessed first", endIndex,
                indicator.getHighestResultIndex());
    }

    @Test
    public void highestResultIndexDoesNotRegressWhenLastBarComputedDuringPrefill() throws Exception {
        double[] data = new double[600];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        BarSeries testSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
        int endIndex = testSeries.getEndIndex();

        CountDownLatch prefillBlocked = new CountDownLatch(1);
        CountDownLatch allowPrefillContinue = new CountDownLatch(1);
        BlockingPrefillIndicator indicator = new BlockingPrefillIndicator(testSeries, 150, prefillBlocked,
                allowPrefillContinue);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> prefillFuture = executor.submit(() -> indicator.getValue(200));
            assertTrue("Prefill did not reach blocking point in time", prefillBlocked.await(30, TimeUnit.SECONDS));

            indicator.forceHighestResultIndex(endIndex);
            assertEquals(endIndex, indicator.getHighestResultIndex());

            allowPrefillContinue.countDown();
            prefillFuture.get(30, TimeUnit.SECONDS);
        } finally {
            allowPrefillContinue.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertEquals("highestResultIndex should not regress after last-bar access", endIndex,
                indicator.getHighestResultIndex());
    }

    /**
     * Simple recursive indicator that sums from 0 to index.
     */
    private final class SumIndicator extends RecursiveCachedIndicator<Num> {

        private SumIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            if (index == 0) {
                return numFactory.numOf(1);
            }
            return getValue(index - 1).plus(numFactory.numOf(1));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    /**
     * Recursive indicator that counts how many times calculate() is called.
     */
    private final class CountingRecursiveIndicator extends RecursiveCachedIndicator<Num> {

        private final AtomicInteger computations = new AtomicInteger(0);

        private CountingRecursiveIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            computations.incrementAndGet();
            if (index == 0) {
                return numFactory.numOf(0);
            }
            return getValue(index - 1).plus(numFactory.numOf(1));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getComputationCount() {
            return computations.get();
        }
    }

    /**
     * Recursive indicator that exposes highestResultIndex for testing.
     */
    private final class TestRecursiveIndicator extends RecursiveCachedIndicator<Num> {

        private TestRecursiveIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            if (index == 0) {
                return numFactory.numOf(0);
            }
            return getValue(index - 1).plus(numFactory.numOf(1));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getHighestResultIndex() {
            return highestResultIndex;
        }

        int getCacheHighestResultIndex() {
            return getCache().getHighestResultIndex();
        }
    }

    private final class BlockingPrefillIndicator extends RecursiveCachedIndicator<Num> {

        private final int blockIndex;
        private final CountDownLatch prefillBlocked;
        private final CountDownLatch allowPrefillContinue;

        private BlockingPrefillIndicator(BarSeries series, int blockIndex, CountDownLatch prefillBlocked,
                CountDownLatch allowPrefillContinue) {
            super(series);
            this.blockIndex = blockIndex;
            this.prefillBlocked = prefillBlocked;
            this.allowPrefillContinue = allowPrefillContinue;
        }

        @Override
        protected Num calculate(int index) {
            if (index == blockIndex) {
                prefillBlocked.countDown();
                try {
                    assertTrue("Timed out waiting to allow prefill to continue",
                            allowPrefillContinue.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return numFactory.numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getHighestResultIndex() {
            return highestResultIndex;
        }

        void forceHighestResultIndex(int index) {
            updateHighestResultIndex(index);
        }
    }

    private final class ReentrantIndicator extends RecursiveCachedIndicator<Num> {

        private final int triggerIndex;

        private ReentrantIndicator(BarSeries series, int triggerIndex) {
            super(series);
            this.triggerIndex = triggerIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index < triggerIndex) {
                getValue(triggerIndex);
            }
            return numFactory.numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private abstract static class LegacyRecursiveCachedIndicator<T> extends CachedIndicator<T> {

        private static final int RECURSION_THRESHOLD = 100;

        protected LegacyRecursiveCachedIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public T getValue(int index) {
            BarSeries series = getBarSeries();
            if (series == null || index > series.getEndIndex()) {
                return super.getValue(index);
            }

            int startIndex = Math.max(series.getRemovedBarsCount(), highestResultIndex);
            if (index - startIndex > RECURSION_THRESHOLD) {
                for (int prevIndex = startIndex; prevIndex < index; prevIndex++) {
                    super.getValue(prevIndex);
                }
            }

            return super.getValue(index);
        }
    }

    private final class LegacyReentrantIndicator extends LegacyRecursiveCachedIndicator<Num> {

        private final int triggerIndex;

        private LegacyReentrantIndicator(BarSeries series, int triggerIndex) {
            super(series);
            this.triggerIndex = triggerIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index < triggerIndex) {
                getValue(triggerIndex);
            }
            return numFactory.numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}

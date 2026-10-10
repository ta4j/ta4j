/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

/**
 * Concurrency regression tests for {@link RecursiveCachedIndicator} that are
 * independent of the {@code NumFactory} parameterization: the heavyweight
 * 200k-bar prefill scenario runs once per suite instead of once per factory.
 */
public class RecursiveCachedIndicatorConcurrencyTest {

    @Test
    public void recursivePrefillSurvivesConcurrentSeriesRevisionChange() throws Exception {
        // A recursive indicator's iterative prefill must survive a concurrent series
        // revision change. The snapshot reconciliation introduced with the moving-
        // series cache sync truncates the ring buffer from within the nested
        // getValue() calls of an in-flight prefill; the gap cannot be refilled
        // iteratively (the prefill depth guard skips the nested prefill), and the
        // recursive fallback then walks the gap index by index until the stack
        // overflows.
        int barCount = 200_000;
        int latchIndex = 150_000;
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        for (int i = 0; i < barCount; i++) {
            series.barBuilder().closePrice(1).add();
        }

        LatchingSelfReferencingIndicator indicator = new LatchingSelfReferencingIndicator(series, latchIndex);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            AtomicReference<Throwable> readerFailure = new AtomicReference<>();
            Future<?> reader = pool.submit(() -> {
                try {
                    indicator.getValue(barCount - 1);
                } catch (Throwable t) {
                    readerFailure.set(t);
                }
            });

            // Wait until the prefill is in flight at the latch index, then mutate a
            // bar BELOW the prefill position while the reader is blocked inside
            // calculate(), so the next nested getValue() deterministically observes
            // the revision change (the mutation cannot be overtaken by the prefill).
            assertTrue("prefill never reached the latch index", indicator.reached.await(120, TimeUnit.SECONDS));
            Bar bar = series.getBar(latchIndex);
            Bar replacement = series.barBuilder()
                    .timePeriod(bar.getTimePeriod())
                    .endTime(bar.getEndTime())
                    .openPrice(500)
                    .highPrice(500)
                    .lowPrice(500)
                    .closePrice(500)
                    .volume(bar.getVolume())
                    .build();
            series.replaceBar(latchIndex - 50_000, replacement);
            indicator.proceed.countDown();

            reader.get(120, TimeUnit.SECONDS);
            assertNull("recursive read crashed: " + readerFailure.get(), readerFailure.get());
            assertNumEquals(barCount, indicator.getValue(barCount - 1));
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class LatchingSelfReferencingIndicator extends RecursiveCachedIndicator<Num> {

        private final int latchIndex;
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);

        private LatchingSelfReferencingIndicator(BarSeries series, int latchIndex) {
            super(series);
            this.latchIndex = latchIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == latchIndex) {
                reached.countDown();
                try {
                    assertTrue("prefill did not resume in time", proceed.await(120, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (index == 0) {
                return getBarSeries().numFactory().one();
            }
            return getValue(index - 1).plus(getBarSeries().numFactory().one());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}

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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    private final class CountingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();

        private CountingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            return numFactory.numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }
    }

    private final class CountingInvalidatableIndicator extends CachedIndicator<Num> {

        private int calculationCount = 0;

        private CountingInvalidatableIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculationCount++;
            return numFactory.numOf(calculationCount);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getCalculationCount() {
            return calculationCount;
        }
    }

}

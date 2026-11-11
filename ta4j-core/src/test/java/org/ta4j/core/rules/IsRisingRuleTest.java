/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IsRisingRuleTest {

    private IsRisingRule rule;
    private BarSeries series;

    @BeforeEach
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        var indicator = new FixedNumIndicator(series, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3);
        rule = new IsRisingRule(indicator, 3);
    }

    @Test
    public void isSatisfied() {
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        // First time to have at least 3 rising values.
        assertTrue(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void doesNotAnchorLookbackBeforeSeriesBeginIndex() {
        BarSeries trimmedSeries = new MockBarSeriesBuilder().withData(9, 8, 7, 6, 0, 1, 2, 3, 4, 5).build();
        trimmedSeries.setMaximumBarCount(5);
        var indicator = new FixedNumIndicator(trimmedSeries, 9, 8, 7, 6, 0, 1, 2, 3, 4, 5);

        assertFalse(new IsRisingRule(indicator, 5).isSatisfied(9));
        assertTrue(new IsRisingRule(indicator, 3).isSatisfied(9));
    }

    @Test
    public void countsStrictRiseExactlyWithLowPrecision() {
        for (RoundingMode rounding : List.of(RoundingMode.HALF_UP, RoundingMode.UNNECESSARY)) {
            NumFactory factory = DecimalNumFactory.getInstance(new MathContext(1, rounding));
            BarSeries prices = new MockBarSeriesBuilder().withNumFactory(factory)
                    .withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 30, 40, 40, 50, 60, 70, 80, 90, 200, 300, 400, 500, 600,
                            700, 800)
                    .build();
            ClosePriceIndicator close = new ClosePriceIndicator(prices);
            IsRisingRule strict = new IsRisingRule(close, 12);
            IsRisingRule elevenOfTwelve = new IsRisingRule(close, 12, 11.0 / 12);

            assertFalse(strict.isSatisfied(11));
            assertTrue(strict.isSatisfied(12));
            assertFalse(strict.isSatisfied(13));
            assertTrue(elevenOfTwelve.isSatisfied(13));
            assertFalse(strict.isSatisfied(24));
            assertTrue(strict.isSatisfied(25));
        }
    }

    @Test
    public void directionalCountsMatchScanForPartialAndOutOfOrderWindows() {
        double[] values = { 5, 5, 6, 2, 2, 3, Double.NaN, 4, 4, 2, 3, 5, -2, 0, 9, 3, 4, 4, 1, 8 };
        BarSeries prices = new BaseBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        FixedNumIndicator source = new FixedNumIndicator(prices, values);
        int[] indexes = { 13, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 8, 19 };
        for (int window : new int[] { 2, 3, 12 }) {
            TrendStrengthIndicator rising = new TrendStrengthIndicator(source, window, true);
            TrendStrengthIndicator falling = new TrendStrengthIndicator(source, window, false);
            IsRisingRule risingRule = new IsRisingRule(source, window, 0.5);
            IsFallingRule fallingRule = new IsFallingRule(source, window, 0.5);
            for (int index : indexes) {
                int rises = 0;
                int falls = 0;
                int start = Math.max(1, index - window + 1);
                for (int i = start; i <= index; i++) {
                    Num current = source.getValue(i);
                    Num previous = source.getValue(i - 1);
                    rises += current.isGreaterThan(previous) ? 1 : 0;
                    falls += current.isLessThan(previous) ? 1 : 0;
                }
                assertEquals(rises, rising.count(index), "rising count at " + index + ", window " + window);
                assertEquals(falls, falling.count(index), "falling count at " + index + ", window " + window);
                assertEquals(rises / (double) window >= 0.5, risingRule.isSatisfied(index));
                assertEquals(falls / (double) window >= 0.5, fallingRule.isSatisfied(index));
            }
        }
    }

    @Test
    public void countsFollowHistoryTerminalAndRetainedHeadChanges() {
        BarSeries prices = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance(1))
                .withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 30, 40, 50, 60, 70, 80)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(prices);
        IsRisingRule strict = new IsRisingRule(close, 12);
        TrendStrengthIndicator counts = new TrendStrengthIndicator(close, 12, true);

        assertEquals(12, counts.count(15));
        assertTrue(strict.isSatisfied(16));
        prices.getBar(7).addPrice(prices.numFactory().zero());
        assertFalse(strict.isSatisfied(16));
        prices.getBar(7).addPrice(prices.numFactory().numOf(7));
        assertTrue(strict.isSatisfied(16));
        prices.getLastBar().addPrice(prices.numFactory().numOf(60));
        assertFalse(strict.isSatisfied(16));
        prices.getLastBar().addPrice(prices.numFactory().numOf(80));
        assertTrue(strict.isSatisfied(16));

        assertEquals(12, counts.count(15));
        prices.setMaximumBarCount(13);
        assertEquals(11, counts.count(15));
        assertEquals(12, counts.count(16));
        assertTrue(strict.isSatisfied(16));
        prices.barBuilder().closePrice(90).add();
        assertTrue(strict.isSatisfied(17));
        assertEquals(12, counts.count(17));
        prices.getBar(prices.getBeginIndex()).addPrice(prices.numFactory().numOf(90));
        assertFalse(strict.isSatisfied(17));
        assertEquals(11, counts.count(17));
    }

    @Test
    public void crossSeriesSourceMutationInvalidatesCounts() {
        BarSeries backing = new MockBarSeriesBuilder().withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 30, 40, 50).build();
        BarSeries dependency = new MockBarSeriesBuilder().withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 30, 40, 50)
                .build();
        ClosePriceIndicator other = new ClosePriceIndicator(dependency);
        CachedIndicator<Num> source = new CachedIndicator<Num>(backing, other) {
            @Override
            protected Num calculate(int index) {
                return other.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        IsRisingRule strict = new IsRisingRule(source, 12);

        assertTrue(strict.isSatisfied(13));
        dependency.getBar(7).addPrice(dependency.numFactory().zero());
        assertFalse(strict.isSatisfied(13));
        dependency.getBar(7).addPrice(dependency.numFactory().numOf(7));
        assertTrue(strict.isSatisfied(13));
        backing.setMaximumBarCount(13);
        assertTrue(strict.isSatisfied(13));
        dependency.getLastBar().addPrice(dependency.numFactory().numOf(20));
        dependency.barBuilder().closePrice(60).add();
        backing.barBuilder().closePrice(60).add();
        assertFalse(strict.isSatisfied(14));
    }

    @Test
    public void sequentialCountsUseLinearSourceReads() {
        int steps = 256;
        for (int window : new int[] { 8, 64 }) {
            for (int extra : new int[] { 0, 1, 2, 4 }) {
                BarSeries prices = new MockBarSeriesBuilder().build();
                if (extra > 0) {
                    prices.setMaximumBarCount(window + extra);
                }
                AtomicInteger reads = new AtomicInteger();
                ClosePriceIndicator close = new ClosePriceIndicator(prices) {
                    @Override
                    public Num getValue(int index) {
                        reads.incrementAndGet();
                        return super.getValue(index);
                    }
                };
                IsRisingRule strict = new IsRisingRule(close, window);
                for (int i = 0; i <= window; i++) {
                    prices.barBuilder().closePrice(i).add();
                }
                assertTrue(strict.isSatisfied(window));
                reads.set(0);
                for (int i = window + 1; i <= window + steps; i++) {
                    prices.barBuilder().closePrice(i).add();
                    assertTrue(strict.isSatisfied(i));
                }
                assertTrue(reads.get() <= 10 * steps, "source reads: " + reads.get());
            }
        }
    }

    @Test
    public void boundedCountsExpireOutgoingPlateausExactly() {
        for (boolean rising : new boolean[] { true, false }) {
            int direction = rising ? 1 : -1;
            BarSeries prices = new MockBarSeriesBuilder()
                    .withData(direction, 2 * direction, 3 * direction, 3 * direction, 4 * direction)
                    .build();
            prices.setMaximumBarCount(5);
            TrendStrengthIndicator counts = new TrendStrengthIndicator(new ClosePriceIndicator(prices), 4, rising);

            assertEquals(3, counts.count(4));
            prices.barBuilder().closePrice(5 * direction).add();
            assertEquals(3, counts.count(5));
            prices.barBuilder().closePrice(6 * direction).add();
            assertEquals(3, counts.count(6));
            prices.barBuilder().closePrice(7 * direction).add();
            assertEquals(4, counts.count(7));
            prices.getLastBar().addPrice(prices.numFactory().numOf(5 * direction));
            assertEquals(3, counts.count(7));
            prices.barBuilder().closePrice(8 * direction).add();
            assertEquals(3, counts.count(8));
        }
    }

    @Test
    public void concurrentBoundedSequentialCountsRemainCoherent() throws Exception {
        BarSeries prices = new MockBarSeriesBuilder().withData(0, 1, 2, 3, 4).build();
        prices.setMaximumBarCount(5);
        ClosePriceIndicator close = new ClosePriceIndicator(prices);
        TrendStrengthIndicator counts = new TrendStrengthIndicator(close, 4, true);
        assertEquals(4, counts.count(4));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int index = 5; index < 37; index++) {
                prices.barBuilder().closePrice(index % 7).add();
                int expected = 0;
                for (int i = index - 3; i <= index; i++) {
                    if (close.getValue(i).isGreaterThan(close.getValue(i - 1))) {
                        expected++;
                    }
                }
                int requestedIndex = index;
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (int thread = 0; thread < 4; thread++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return counts.count(requestedIndex);
                    }));
                }
                start.countDown();
                for (Future<Integer> result : results) {
                    assertEquals(expected, result.get(10, TimeUnit.SECONDS).intValue());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentReadsKeepCountsCoherent() throws Exception {
        BarSeries prices = new MockBarSeriesBuilder().withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 30, 40, 40).build();
        ClosePriceIndicator close = new ClosePriceIndicator(prices);
        IsRisingRule strict = new IsRisingRule(close, 12);
        IsRisingRule elevenOfTwelve = new IsRisingRule(close, 12, 11.0 / 12);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        assertTrue(strict.isSatisfied(12));
                        assertFalse(strict.isSatisfied(13));
                        assertTrue(elevenOfTwelve.isSatisfied(13));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}

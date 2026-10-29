/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.PortfolioFixtures.START;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class LivePortfolioSeriesTest {

    private static final NumFactory DOUBLE = DoubleNumFactory.getInstance();

    @Test
    public void appendsTimesAndAddsOrRemovesAssetsWithoutRenumberingHistory() {
        BarSeries alpha = new BaseBarSeriesBuilder().withName("ALPHA")
                .withNumFactory(DOUBLE)
                .withBars(List.of(bar(0, 100), bar(2, 120)))
                .build();
        BarSeries beta = series("BETA", DOUBLE, 1, "50", "55", "60");
        Map<String, BarSeries> sources = new LinkedHashMap<>();
        sources.put("ALPHA", alpha);
        sources.put("BETA", beta);
        LivePortfolioSeries view = new LivePortfolioSeries(sources);
        sources.clear();

        assertEquals(List.of("ALPHA", "BETA"), view.assets());
        assertEquals(3, view.getEndIndex());
        assertEquals(START.plus(Duration.ofDays(3)), view.getEndTime(3));
        assertFalse(view.hasBar("BETA", 0));
        assertEquals(-1, view.getSourceIndex("BETA", 0));
        assertFalse(view.hasBar("ALPHA", 1));
        assertEquals(0, view.getSourceIndex("ALPHA", 1));
        assertTrue(view.hasBar("ALPHA", 2));
        assertEquals(1, view.getSourceIndex("ALPHA", 3));
        assertSame(alpha, view.getBarSeries("ALPHA"));

        alpha.addBar(bar(4, 140));
        assertEquals(3, view.getEndIndex());
        assertEquals(1, view.advance());
        assertEquals(0, view.advance());
        assertEquals(START.plus(Duration.ofDays(4)), view.getEndTime(4));

        BarSeries gamma = new BaseBarSeriesBuilder().withName("GAMMA")
                .withNumFactory(DOUBLE)
                .withBeginIndex(30)
                .withBars(List.of(bar(1, 31), bar(4, 34), bar(5, 35), bar(7, 37)))
                .build();
        view.addAsset("GAMMA", gamma);
        assertEquals(List.of("ALPHA", "BETA", "GAMMA"), view.assets());
        assertEquals(6, view.getEndIndex());
        assertFalse(view.hasBar("GAMMA", 0));
        assertEquals(-1, view.getSourceIndex("GAMMA", 0));
        assertTrue(view.hasBar("GAMMA", 1));
        assertEquals(30, view.getSourceIndex("GAMMA", 3));
        assertTrue(view.hasBar("GAMMA", 4));
        assertEquals(31, view.getSourceIndex("GAMMA", 4));
        assertEquals(START.plus(Duration.ofDays(5)), view.getEndTime(5));
        assertEquals(START.plus(Duration.ofDays(7)), view.getEndTime(6));
        List<String> priorAssets = view.assets();

        assertTrue(view.removeAsset("GAMMA"));
        assertFalse(view.removeAsset("GAMMA"));
        assertEquals(List.of("ALPHA", "BETA", "GAMMA"), priorAssets);
        assertEquals(List.of("ALPHA", "BETA"), view.assets());
        assertEquals(6, view.getEndIndex());
        assertFalse(view.hasBar("GAMMA", 4));
        assertEquals(-1, view.getSourceIndex("GAMMA", 4));
        assertThrows(IllegalArgumentException.class, () -> view.getBarSeries("GAMMA"));
        assertEquals(4, gamma.getBarCount());

        BarSeries relisted = series("RELISTED", DOUBLE, 6, "66");
        view.addAsset("GAMMA", relisted);
        assertSame(relisted, view.getBarSeries("GAMMA"));
        assertEquals(6, view.getEndIndex()); // day 6 cannot be inserted before the published day 7
        assertFalse(view.hasBar("GAMMA", 6));
        assertEquals(0, view.getSourceIndex("GAMMA", 6));
        assertThrows(UnsupportedOperationException.class, () -> view.assets().add("NO"));
        assertThrows(IndexOutOfBoundsException.class, () -> view.hasBar("ALPHA", -1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.getSourceIndex("MISSING", 7));
    }

    @Test
    public void retentionAndBarReplacementsAreObservedWithoutReturningRemovedIndexes() {
        ConcurrentBarSeries source = new ConcurrentBarSeriesBuilder().withName("A")
                .withNumFactory(DOUBLE)
                .withMaxBarCount(2)
                .withBars(List.of(bar(0, 10), bar(1, 11)))
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", source));

        source.addBar(bar(2, 12));
        assertFalse(view.hasBar("A", 0));
        assertEquals(-1, view.getSourceIndex("A", 0));
        assertEquals(1, view.getSourceIndex("A", 1));
        assertEquals(1, view.advance());
        assertEquals(2, view.getSourceIndex("A", 2));
        source.addPrice(DOUBLE.numOf(13));
        assertNumEquals(13, view.getBarSeries("A").getLastBar().getClosePrice());

        source.replaceBar(1, bar(3, 30));
        source.replaceBar(2, bar(1, 21));
        assertEquals(2, view.getSourceIndex("A", 1));
        assertFalse(view.hasBar("A", 2));
        assertEquals(2, view.getSourceIndex("A", 2));
        assertEquals(1, view.advance());
        assertEquals(1, view.getSourceIndex("A", 3));
    }

    @Test
    public void reorderedAndDuplicateReplacementTimesUseLatestTimeThenGreatestIndex() {
        ConcurrentBarSeries source = new ConcurrentBarSeriesBuilder().withName("A")
                .withNumFactory(DOUBLE)
                .withBars(List.of(bar(0, 10), bar(1, 11), bar(2, 12)))
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", source));

        source.replaceBar(0, bar(3, 30));
        source.replaceBar(2, bar(1, 21));
        assertFalse(view.hasBar("A", 0));
        assertEquals(-1, view.getSourceIndex("A", 0));
        assertTrue(view.hasBar("A", 1));
        assertEquals(2, view.getSourceIndex("A", 1));
        assertFalse(view.hasBar("A", 2));
        assertEquals(2, view.getSourceIndex("A", 2));
        assertEquals(1, view.advance());
        assertEquals(0, view.getSourceIndex("A", 3));
        assertEquals(START, view.getEndTime(0));
    }

    @Test
    public void allowsInitiallyEmptySeriesAndValidatesNumericFactories() {
        NumFactory decimal = DecimalNumFactory.getInstance(8);
        BarSeries empty = new BaseBarSeriesBuilder().withName("A").withNumFactory(decimal).build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", empty));

        assertEquals(0, view.getBeginIndex());
        assertEquals(-1, view.getEndIndex());
        assertEquals(0, view.advance());
        assertThrows(IndexOutOfBoundsException.class, () -> view.getEndTime(0));
        view.addAsset("B", series("B", DecimalNumFactory.getInstance(8), 0, "1"));
        assertEquals(0, view.getEndIndex());
        assertFalse(view.hasBar("A", 0));
        assertEquals(-1, view.getSourceIndex("A", 0));
        empty.addBar(series("A-NEW", decimal, 2, "3").getFirstBar());
        assertEquals(1, view.advance());
        assertTrue(view.hasBar("A", 1));
        assertEquals(0, view.getSourceIndex("A", 1));
        empty.clear();
        assertFalse(view.hasBar("A", 1));
        assertEquals(-1, view.getSourceIndex("A", 1));
        empty.addBar(series("A-RESET", decimal, 1, "4").getFirstBar());
        assertEquals(0, view.advance());
        assertFalse(view.hasBar("A", 1));
        assertEquals(0, view.getSourceIndex("A", 1));
        empty.addBar(series("A-NEXT", decimal, 3, "5").getFirstBar());
        assertEquals(1, view.advance());
        assertTrue(view.hasBar("A", 2));
        assertEquals(1, view.getSourceIndex("A", 2));
        assertThrows(IllegalArgumentException.class, () -> view.addAsset("B", empty));
        assertThrows(IllegalArgumentException.class,
                () -> view.addAsset("C", series("C", DecimalNumFactory.getInstance(9), 0, "1")));
        assertThrows(IllegalArgumentException.class, () -> view.addAsset("C", series("C", 1)));
        assertThrows(IllegalArgumentException.class, () -> view.addAsset(" ", empty));
        assertThrows(IllegalArgumentException.class, () -> new LivePortfolioSeries(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LivePortfolioSeries(Map.of("A", empty, "B", series("B", 1))));
        assertTrue(view.removeAsset("A"));
        assertTrue(view.removeAsset("B"));
        assertTrue(view.assets().isEmpty());
        assertEquals(2, view.getEndIndex());
        assertFalse(view.hasBar("B", 0));
    }

    @Test
    public void concurrentAdvancesPublishEachTimeOnce() throws Exception {
        ConcurrentBarSeries source = new ConcurrentBarSeriesBuilder().withName("A")
                .withNumFactory(DOUBLE)
                .withBars(List.of(bar(0, 10)))
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", source));
        source.addBar(bar(1, 11));
        source.addBar(bar(2, 12));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return view.advance();
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return view.advance();
            });
            start.countDown();
            assertEquals(2, first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS));
            assertEquals(2, view.getEndIndex());
            assertEquals(START.plus(Duration.ofDays(1)), view.getEndTime(1));
            assertEquals(START.plus(Duration.ofDays(2)), view.getEndTime(2));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentRetentionAndLookupsKeepPublishedIndexesStable() throws Exception {
        ConcurrentBarSeries source = new ConcurrentBarSeriesBuilder().withName("A")
                .withNumFactory(DOUBLE)
                .withMaxBarCount(3)
                .withBars(List.of(bar(0, 10)))
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", source));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier round = new CyclicBarrier(2);
        int rounds = 16;
        try {
            Future<?> writer = executor.submit(() -> {
                for (int day = 1; day <= rounds; day++) {
                    round.await(5, TimeUnit.SECONDS);
                    source.addBar(bar(day, 10 + day));
                    view.advance();
                    round.await(5, TimeUnit.SECONDS);
                }
                return null;
            });
            Future<?> reader = executor.submit(() -> {
                for (int day = 1; day <= rounds; day++) {
                    round.await(5, TimeUnit.SECONDS);
                    int end = view.getEndIndex();
                    assertEquals(START.plus(Duration.ofDays(end)), view.getEndTime(end));
                    int asOf = view.getSourceIndex("A", end);
                    assertTrue(asOf == -1 || (asOf >= 0 && asOf <= end));
                    view.hasBar("A", end);
                    round.await(5, TimeUnit.SECONDS);
                }
                return null;
            });
            writer.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
            assertEquals(rounds, view.getEndIndex());
            assertEquals(-1, view.getSourceIndex("A", 0));
            assertEquals(rounds, view.getSourceIndex("A", rounds));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Bar bar(int day, double close) {
        return series("fixture", DOUBLE, day, Double.toString(close)).getFirstBar();
    }
}

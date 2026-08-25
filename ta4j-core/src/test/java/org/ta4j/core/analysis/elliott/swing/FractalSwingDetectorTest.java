/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class FractalSwingDetectorTest {

    @Test
    void cachedIndicatorReuseMatchesFreshDetectorResults() {
        final BarSeries series = noisySeries(200, 42L);
        final FractalSwingDetector shared = new FractalSwingDetector(2);

        for (int index = 0; index < series.getBarCount(); index++) {
            assertThat(shared.detectPivots(series, index)).as("cached result at bar " + index)
                    .isEqualTo(new FractalSwingDetector(2).detectPivots(series, index));
        }
    }

    @Test
    void indicatorCacheSeparatesSeriesWithoutCrossTalk() {
        final BarSeries first = noisySeries(120, 1L);
        final BarSeries second = noisySeries(150, 2L);
        final FractalSwingDetector shared = new FractalSwingDetector(2);

        for (int index = 0; index <= seriesEnd(first); index++) {
            assertThat(shared.detectPivots(first, index))
                    .isEqualTo(new FractalSwingDetector(2).detectPivots(first, index));
        }
        for (int index = 0; index <= seriesEnd(second); index++) {
            assertThat(shared.detectPivots(second, index))
                    .isEqualTo(new FractalSwingDetector(2).detectPivots(second, index));
        }
    }

    @Test
    void boundedCacheEvictionKeepsResultsCorrect() {
        // More distinct series than MAX_CACHED_SERIES; evicted entries must not
        // corrupt later evaluations of re-touched or fresh series.
        final List<BarSeries> seriesList = new ArrayList<>();
        for (long seed = 10; seed < 22; seed++) {
            seriesList.add(noisySeries(90, seed));
        }
        final FractalSwingDetector shared = new FractalSwingDetector(2);
        for (int pass = 0; pass < 2; pass++) {
            for (final BarSeries series : seriesList) {
                assertThat(shared.detectPivots(series, series.getEndIndex()))
                        .isEqualTo(new FractalSwingDetector(2).detectPivots(series, series.getEndIndex()));
            }
        }
    }

    @Test
    void descendingAndRepeatedQueriesMatchFreshDetectorResults() {
        final BarSeries series = noisySeries(160, 7L);
        final FractalSwingDetector shared = new FractalSwingDetector(2);
        final int end = seriesEnd(series);

        // Ascend past the target indices first so the later queries exercise
        // the non-ascending and repeated-query paths of the replay state.
        for (int index = 0; index <= end; index++) {
            shared.detectPivots(series, index);
        }
        for (int index = end; index >= 0; index--) {
            assertThat(shared.detectPivots(series, index)).as("descending result at bar " + index)
                    .isEqualTo(new FractalSwingDetector(2).detectPivots(series, index));
        }
        for (int index = 0; index <= end; index++) {
            assertThat(shared.detectPivots(series, index)).as("re-ascending result at bar " + index)
                    .isEqualTo(new FractalSwingDetector(2).detectPivots(series, index));
        }
    }

    @Test
    void seriesGrowthKeepsReplayResultsConsistentWithFreshDetection() {
        final SplittableRandom random = new SplittableRandom(11L);
        final BarSeries series = new MockBarSeriesBuilder().build();
        final FractalSwingDetector shared = new FractalSwingDetector(2);
        double price = 100;

        for (int stage = 0; stage < 3; stage++) {
            for (int bars = 0; bars < 60; bars++) {
                price *= 1 + random.nextDouble(-0.02, 0.02);
                final double close = price;
                series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
            }
            for (int index = 0; index <= seriesEnd(series); index++) {
                assertThat(shared.detectPivots(series, index))
                        .as("grown-series result at bar " + index + " in stage " + stage)
                        .isEqualTo(new FractalSwingDetector(2).detectPivots(series, index));
            }
        }
    }

    @Test
    void ascendingReplayDoesNotRebuildTheFullPivotPrefixPerIndex() {
        final CountedZigZagSeries small = new CountedZigZagSeries(1_200);
        final FractalSwingDetector smallDetector = new FractalSwingDetector(2);
        final long smallReads = replayAscending(smallDetector, small);
        assertThat(smallReads).isPositive();
        assertThat(smallDetector.detectPivots(small.series(), small.seriesEnd()))
                .isEqualTo(new FractalSwingDetector(2).detectPivots(small.series(), small.seriesEnd()));

        final CountedZigZagSeries large = new CountedZigZagSeries(2_400);
        final FractalSwingDetector largeDetector = new FractalSwingDetector(2);
        final long largeReads = replayAscending(largeDetector, large);

        // Incremental replay work doubles with the series length. Rebuilding
        // the cumulative pivot prefix at every as-of index would re-price all
        // known pivots per index and roughly quadruple the counted reads.
        assertThat(largeReads).isLessThan(3 * smallReads);
    }

    @Test
    void staggeredSameIndexSidesReconcileInsteadOfAppendingZeroLengthSwings() {
        // With window 1 the LOW@1 (price 0) confirms at bar 2 while the HIGH
        // plateau [1..2] only completes at bar 3, so the opposite-type side
        // reaches pivot index 1 one bar later than the low side did.
        final BarSeries series = seriesWithHighsAndLows(new double[] { 5, 10, 10, 5, 4, 6, 8, 3 },
                new double[] { 5, 0, 5, 5, 2, 3, 4, 2 });
        final FractalSwingDetector shared = new FractalSwingDetector(1);

        for (int index = 0; index <= series.getEndIndex(); index++) {
            assertThat(shared.detectPivots(series, index)).as("staggered result at bar " + index)
                    .isEqualTo(new FractalSwingDetector(1).detectPivots(series, index));
        }

        assertThat(shared.detectPivots(series, series.getEndIndex())).extracting(SwingPivot::index, SwingPivot::type)
                .containsExactly(tuple(1, SwingPivotType.HIGH), tuple(4, SwingPivotType.LOW),
                        tuple(6, SwingPivotType.HIGH));
    }

    @Test
    void repeatedQueriesReuseTheCachedDetectionResultWithoutRematerializing() {
        final BarSeries series = noisySeries(120, 5L);
        final FractalSwingDetector shared = new FractalSwingDetector(2);
        final int end = seriesEnd(series);

        for (int index = 0; index <= end; index++) {
            shared.detectPivots(series, index);
        }
        final List<SwingPivot> tail = shared.detectPivots(series, end);
        // Unchanged replay state must not rebuild the cumulative swing chain:
        // rebuilding would allocate an ElliottSwing for every accumulated pivot
        // on every query, keeping causal replay quadratic in transient
        // allocations. Instance identity proves the cached snapshot is reused.
        assertThat(shared.detectPivots(series, end)).isSameAs(tail);
        assertThat(shared.detectPivots(series, end)).isSameAs(tail);

        final List<SwingPivot> middle = shared.detectPivots(series, end / 2);
        assertThat(shared.detectPivots(series, end / 2)).isSameAs(middle);
    }

    private static long replayAscending(final FractalSwingDetector detector, final CountedZigZagSeries fixture) {
        final long readsBefore = fixture.priceReads.sum();
        for (int index = 0; index <= fixture.seriesEnd(); index++) {
            detector.detectPivots(fixture.series(), index);
        }
        return fixture.priceReads.sum() - readsBefore;
    }

    /** Zigzag fixture whose bars count high/low price reads during detection. */
    private static final class CountedZigZagSeries {

        private final BarSeries series = new MockBarSeriesBuilder().build();
        private final LongAdder priceReads = new LongAdder();

        private CountedZigZagSeries(final int barCount) {
            final NumFactory factory = series.numFactory();
            double price = 100;
            int direction = 1;
            for (int index = 0; index < barCount; index++) {
                price += 2 * direction;
                if ((index + 1) % 4 == 0) {
                    direction = -direction;
                }
                final Instant beginTime = Instant.EPOCH.plus(Duration.ofMinutes(index));
                final Num value = factory.numOf(price);
                series.addBar(new CountingBar(priceReads, factory, beginTime, value));
            }
        }

        private BarSeries series() {
            return series;
        }

        private int seriesEnd() {
            return series.getEndIndex();
        }
    }

    private static final class CountingBar extends BaseBar {

        private static final long serialVersionUID = 1L;

        private final transient LongAdder priceReads;

        private CountingBar(final LongAdder priceReads, final NumFactory factory, final Instant beginTime,
                final Num price) {
            super(Duration.ofMinutes(1), beginTime, beginTime.plus(Duration.ofMinutes(1)), price, price, price, price,
                    factory.numOf(1), factory.numOf(0), 0L);
            this.priceReads = priceReads;
        }

        @Override
        public Num getHighPrice() {
            priceReads.increment();
            return super.getHighPrice();
        }

        @Override
        public Num getLowPrice() {
            priceReads.increment();
            return super.getLowPrice();
        }
    }

    private static int seriesEnd(final BarSeries series) {
        return series.getEndIndex() - series.getBeginIndex();
    }

    private static BarSeries noisySeries(final int barCount, final long seed) {
        final SplittableRandom random = new SplittableRandom(seed);
        final BarSeries series = new MockBarSeriesBuilder().build();
        double price = 100;
        for (int index = 0; index < barCount; index++) {
            price *= 1 + random.nextDouble(-0.02, 0.02);
            final double close = price;
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
        }
        return series;
    }

    private static BarSeries seriesWithHighsAndLows(final double[] highs, final double[] lows) {
        final BarSeries series = new MockBarSeriesBuilder().build();
        for (int index = 0; index < highs.length; index++) {
            final double close = (highs[index] + lows[index]) / 2;
            series.barBuilder()
                    .openPrice(close)
                    .highPrice(highs[index])
                    .lowPrice(lows[index])
                    .closePrice(close)
                    .volume(1)
                    .add();
        }
        return series;
    }
}

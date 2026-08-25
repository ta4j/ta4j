/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class FractalSwingDetectorTest {

    @Test
    void cachedIndicatorReuseMatchesFreshDetectorResults() {
        final BarSeries series = noisySeries(200, 42L);
        final FractalSwingDetector shared = new FractalSwingDetector(2);

        for (int index = 0; index < series.getBarCount(); index++) {
            assertThat(shared.detectPivots(series, index))
                    .as("cached result at bar " + index)
                    .isEqualTo(new FractalSwingDetector(2).detectPivots(series, index));
        }
    }

    @Test
    void indicatorCacheSeparatesSeriesWithoutCrossTalk() {
        final BarSeries first = noisySeries(120, 1L);
        final BarSeries second = noisySeries(150, 2L);
        final FractalSwingDetector shared = new FractalSwingDetector(2);

        for (int index = 0; index <= seriesEnd(first); index++) {
            assertThat(shared.detectPivots(first, index)).isEqualTo(new FractalSwingDetector(2).detectPivots(first,
                    index));
        }
        for (int index = 0; index <= seriesEnd(second); index++) {
            assertThat(shared.detectPivots(second, index)).isEqualTo(new FractalSwingDetector(2).detectPivots(second,
                    index));
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
            series.barBuilder()
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .add();
        }
        return series;
    }
}

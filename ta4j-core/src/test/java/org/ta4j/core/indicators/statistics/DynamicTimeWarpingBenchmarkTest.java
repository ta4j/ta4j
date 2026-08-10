/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Regression instrument for the CF-454 DTW baseline performance: rolling
 * windows of 64, 128, 512, and 2,048 bars under Sakoe–Chiba radii 0, 5, and 20.
 *
 * <p>
 * This is not a brittle wall-clock assertion: it verifies result correctness at
 * scale and records calculated-index time for the log. Excluded from the
 * default and full gates via the {@code benchmark} tag.
 * </p>
 */
@Tag("benchmark")
class DynamicTimeWarpingBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(DynamicTimeWarpingBenchmarkTest.class);

    private static final int BARS = 20_000;

    @Test
    void evaluatesRollingWindowsAcrossRadii() {
        BarSeries series = series();
        Indicator<Num> first = sineIndicator(series, 0);
        Indicator<Num> second = sineIndicator(series, 3);
        for (int windowSize : new int[] { 64, 128, 512, 2_048 }) {
            for (int radius : new int[] { 0, 5, 20 }) {
                DynamicTimeWarpingConfig config = new DynamicTimeWarpingConfig(SequenceNormalization.Z_SCORE,
                        LocalDistance.SQUARED, WarpingWindow.sakoeChiba(radius), PathCostNormalization.BY_PATH_LENGTH);
                DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(first, second,
                        windowSize, config);
                int unstableBars = dtw.getCountOfUnstableBars();
                int firstIndex = Math.min(BARS - 1, unstableBars);
                int lastIndex = BARS - 1;
                long start = System.nanoTime();
                Num value = dtw.getValue(firstIndex);
                for (int index = firstIndex + 1; index <= lastIndex; index++) {
                    value = dtw.getValue(index);
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                int calculatedIndexes = lastIndex - firstIndex + 1;
                assertFalse(value.isNaN(), "expected a finite distance at the final index");
                LOG.info("dtw benchmark: window {} radius {} -> {} indexes in {} ms ({}/s)", windowSize, radius,
                        calculatedIndexes, elapsedMs, calculatedIndexes * 1000L / Math.max(1L, elapsedMs));
            }
        }
    }

    private static BarSeries series() {
        double[] closes = new double[BARS];
        for (int i = 0; i < BARS; i++) {
            closes[i] = 1000.0 + 100.0 * Math.sin(i / 37.0);
        }
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(closes).build();
    }

    private static Indicator<Num> sineIndicator(BarSeries series, int phaseOffset) {
        Num[] values = new Num[BARS];
        DoubleNumFactory factory = DoubleNumFactory.getInstance();
        for (int i = 0; i < BARS; i++) {
            values[i] = factory.numOf(Math.sin(2.0 * Math.PI * (i + phaseOffset) / 64.0));
        }
        return new MockIndicator(series, 0, values);
    }
}

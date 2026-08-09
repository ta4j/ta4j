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
 * Regression instrument for the CF-454 TLCC profile baseline performance: lag
 * ranges {@code [-20, 20]} and {@code [-100, 100]} over windows of 128, 512,
 * and 2,048 bars.
 *
 * <p>
 * This is not a brittle wall-clock assertion: it verifies result correctness at
 * scale and records profile time for the log. Excluded from the default and
 * full gates via the {@code benchmark} tag.
 * </p>
 */
@Tag("benchmark")
class LeadLagCorrelationAnalyzerBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(LeadLagCorrelationAnalyzerBenchmarkTest.class);

    private static final int BARS = 20_000;

    @Test
    void evaluatesProfilesAcrossWindowAndLagSizes() {
        BarSeries series = series();
        Indicator<Num> first = sineIndicator(series, 0);
        Indicator<Num> second = sineIndicator(series, 5);
        LeadLagCorrelationAnalyzer analyzer = new LeadLagCorrelationAnalyzer();
        for (int barCount : new int[] { 128, 512, 2_048 }) {
            for (int[] lagRange : new int[][] { { -20, 20 }, { -100, 100 } }) {
                long start = System.nanoTime();
                LagCorrelationProfile profile = analyzer.analyze(first, second, BARS - 1, barCount, lagRange[0],
                        lagRange[1], LagSelectionPolicy.MAXIMUM_ABSOLUTE_CORRELATION);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                assertFalse(profile.bestLags().isEmpty(), "expected at least one defined lag");
                LOG.info("tlcc benchmark: window {} lag range [{},{}] -> {} ms, {} points, best {}", barCount,
                        lagRange[0], lagRange[1], elapsedMs, profile.points().size(), profile.selectedLag());
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

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Regression instrument for the CF-454 event-MI baseline performance: 10,000
 * and 100,000 bars with event prevalence near 1%, 5%, and 20%, under 8, 16, and
 * 32 predictor bins with both binning strategies.
 *
 * <p>
 * This is not a brittle wall-clock assertion: it verifies result correctness at
 * scale and records evaluation time for the log. Excluded from the default and
 * full gates via the {@code benchmark} tag.
 * </p>
 */
@Tag("benchmark")
class EventMutualInformationBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(EventMutualInformationBenchmarkTest.class);

    @Test
    void evaluatesEventMutualInformationAcrossSizesAndPrevalences() {
        for (int bars : new int[] { 10_000, 100_000 }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                    .withData(closes(bars))
                    .build();
            Indicator<Num> predictor = new ClosePriceIndicator(series);
            for (double prevalence : new double[] { 0.01, 0.05, 0.20 }) {
                int stride = (int) Math.round(1.0 / prevalence);
                Indicator<Boolean> target = new CachedIndicator<Boolean>(series) {
                    @Override
                    protected Boolean calculate(int index) {
                        return index % stride == 0;
                    }

                    @Override
                    public int getCountOfUnstableBars() {
                        return 0;
                    }
                };
                for (int bins : new int[] { 8, 16, 32 }) {
                    for (BinningStrategy strategy : BinningStrategy.values()) {
                        long start = System.nanoTime();
                        EventMutualInformationResult result = new EventMutualInformationEvaluator().evaluate(predictor,
                                target, 0, bars - 1,
                                new EventMutualInformationConfig(0, 3, bins, strategy, HistoryPolicy.CLAMP));
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                        assertFalse(result.mutualInformationNats().isNaN(),
                                "expected a defined MI at " + bars + " bars");
                        LOG.info(
                                "event-mi benchmark: {} bars, prevalence {}, {} bins, {} -> {} ms, {} samples, "
                                        + "{} positives, effective bins {}",
                                bars, prevalence, bins, strategy, elapsedMs, result.sampleCount(),
                                result.positiveTargetCount(), result.effectiveBinCount());
                    }
                }
            }
        }
    }

    private static double[] closes(int bars) {
        double[] closes = new double[bars];
        for (int i = 0; i < bars; i++) {
            closes[i] = 1000.0 + 100.0 * Math.sin(i / 97.0);
        }
        return closes;
    }
}

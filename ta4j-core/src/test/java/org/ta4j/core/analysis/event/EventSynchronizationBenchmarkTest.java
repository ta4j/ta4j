/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Regression instrument for the CF-453 baseline performance requirements:
 * sparse events over 100,000 bars with roughly 1,000 predicted and 1,000
 * reference events, evaluated under 0-, 3-, and 10-bar tolerance windows.
 *
 * <p>
 * This is not a brittle wall-clock assertion: it verifies result correctness at
 * scale and records extraction/matching time and result size for the log.
 * Excluded from the default and full gates via the {@code benchmark} tag.
 */
@Tag("benchmark")
class EventSynchronizationBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(EventSynchronizationBenchmarkTest.class);

    private static final int BARS = 100_000;
    private static final int EVENT_STRIDE = 100;

    @Test
    void evaluatesSparseEventsOverHundredThousandBars() {
        double[] closes = new double[BARS];
        for (int i = 0; i < BARS; i++) {
            closes[i] = 1000.0 + 100.0 * Math.sin(i / 97.0);
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(closes)
                .build();
        EventSynchronizationEvaluator evaluator = new EventSynchronizationEvaluator();

        for (int window : new int[] { 0, 3, 10 }) {
            long start = System.nanoTime();
            EventSynchronizationResult result = evaluator.evaluate(i -> i % EVENT_STRIDE == 0,
                    i -> i % EVENT_STRIDE == (window == 0 ? 0 : 2), series, 0, 0, 0, BARS - 1,
                    new EventSynchronizationConfig(window, window));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals(BARS / EVENT_STRIDE, result.matchedCount());
            LOG.info("event-sync benchmark: window {} -> {} ms, {} matches, {} + {} unmatched, mean |offset| {}",
                    window, elapsedMs, result.matchedCount(), result.unmatchedPredictedIndexes().size(),
                    result.unmatchedReferenceIndexes().size(), result.meanAbsoluteOffset());
        }
    }
}

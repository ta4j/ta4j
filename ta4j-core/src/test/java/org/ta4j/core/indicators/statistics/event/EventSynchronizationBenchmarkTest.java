/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Regression instrument for the CF-453 performance requirements: sparse events
 * over 100,000 bars with roughly 1,000 predicted and 1,000 reference events,
 * evaluated both as one terminal range and as every rolling window of a 200-bar
 * indicator.
 *
 * <p>
 * This is not a brittle wall-clock assertion: it verifies result correctness at
 * scale and records extraction/matching time for the log. Excluded from the
 * default and full gates via the {@code benchmark} tag.
 */
@Tag("benchmark")
class EventSynchronizationBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(EventSynchronizationBenchmarkTest.class);

    private static final int BARS = 100_000;
    private static final int EVENT_STRIDE = 100;

    private BarSeries sineSeries() {
        double[] closes = new double[BARS];
        for (int i = 0; i < BARS; i++) {
            closes[i] = 1000.0 + 100.0 * Math.sin(i / 97.0);
        }
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(closes).build();
    }

    private Indicator<Boolean> eventsAt(BarSeries series, int stride) {
        return new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                return index % stride == 0;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }

    @Test
    void evaluatesSparseEventsOverHundredThousandBarsAsOneRange() {
        BarSeries series = sineSeries();
        for (int window : new int[] { 0, 3, 10 }) {
            long start = System.nanoTime();
            EventSynchronizationResult result = EventSynchronizationSupport.synchronize(
                    EventSignals.fromPredicate(series, 0, i -> i % EVENT_STRIDE == 0),
                    EventSignals.fromPredicate(series, 0, i -> i % EVENT_STRIDE == (window == 0 ? 0 : 2)), 0, BARS - 1,
                    window, window);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals(BARS / EVENT_STRIDE, result.matchedCount());
            LOG.info(
                    "event-sync benchmark (one range): window {} -> {} ms, {} matches, {} + {} unmatched, mean |offset| {}",
                    window, elapsedMs, result.matchedCount(), result.unmatchedPredictedIndexes().size(),
                    result.unmatchedReferenceIndexes().size(), result.meanAbsoluteOffset());
        }
    }

    @Test
    void evaluatesEveryRollingWindowAcrossHundredThousandBars() {
        BarSeries series = sineSeries();
        Indicator<Boolean> predicted = eventsAt(series, EVENT_STRIDE);
        Indicator<Boolean> reference = eventsAt(series, EVENT_STRIDE);
        EventSynchronizationIndicator indicator = new EventSynchronizationIndicator(predicted, reference, 200, 0, 0);

        // Every 200-bar window of two perfectly coincident stride-100 streams
        // contains events from both sides and must score exactly 1.0.
        long start = System.nanoTime();
        for (int i = 0; i < BARS; i++) {
            indicator.getValue(i);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(1.0, indicator.getValue(50_000).doubleValue(), 1e-12);
        assertEquals(1.0, indicator.getValue(BARS - 1).doubleValue(), 1e-12);
        EventSynchronizationIndicator.Result terminal = indicator.getResult(BARS - 1);
        assertEquals(2, terminal.matchedCount());
        assertEquals(0, terminal.falsePositives());
        assertEquals(0, terminal.falseNegatives());
        LOG.info("event-sync benchmark (every index): {} bars, 200-bar window -> {} ms total, terminal window {}", BARS,
                elapsedMs, terminal);
    }
}

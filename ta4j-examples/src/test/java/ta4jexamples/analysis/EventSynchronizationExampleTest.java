/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.statistics.EventSynchronizationIndicator.Result;

/**
 * Companion regression check for {@link EventSynchronizationExample}.
 *
 * <p>
 * Runs the demo over its committed Coinbase BTC daily dataset and verifies both
 * workflows score the momentum crossings against causal ZigZag confirmation
 * events exactly: the swing-high workflow matches all 7 predictions against 25
 * confirmations (F1 7/16) and the swing-low workflow matches all 8 predictions
 * against 24 confirmations (F1 1/2), with every offset inside the configured
 * ±12 tolerance window.
 */
@Tag("analysis-demo")
class EventSynchronizationExampleTest {

    @Test
    void demoScoresBothSwingDirections() {
        EventSynchronizationExample.DemoResult demo = EventSynchronizationExample.run();

        assertExact(demo.swingHighs(), 7, 25, 7, 7.0 / 16.0);
        assertExact(demo.swingLows(), 8, 24, 8, 0.5);
        for (Result result : new Result[] { demo.swingHighs(), demo.swingLows() }) {
            assertEquals(result.predictedCount(), result.matchedCount() + result.unmatchedPredictedIndexes().size());
            assertEquals(result.referenceCount(), result.matchedCount() + result.unmatchedReferenceIndexes().size());
            for (org.ta4j.core.indicators.statistics.EventSynchronizationIndicator.Result.Match match : result
                    .matches()) {
                assertTrue(match.offsetBars() >= -12 && match.offsetBars() <= 12,
                        "offset outside the demo tolerance window: " + match);
            }
        }
    }

    private static void assertExact(Result result, int predictedCount, int referenceCount, int matchedCount,
            double f1Score) {
        assertEquals(predictedCount, result.predictedCount());
        assertEquals(referenceCount, result.referenceCount());
        assertEquals(matchedCount, result.matchedCount());
        // Every prediction matches, so precision is exactly 1.0 and recall is
        // the matched share of the reference events.
        assertEquals(1.0, result.precision().doubleValue(), 1e-12);
        assertEquals((double) matchedCount / referenceCount, result.recall().doubleValue(), 1e-12);
        assertEquals(f1Score, result.f1Score().doubleValue(), 1e-12);
    }
}

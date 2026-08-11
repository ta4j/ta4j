/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.event.EventSynchronizationIndicator.Result;

/**
 * Companion regression check for {@link EventSynchronizationExample}.
 *
 * <p>
 * Runs the deterministic demo and verifies both workflows score the momentum
 * crossings against causal ZigZag confirmation events exactly: the swing-high
 * workflow matches 9 of 10 confirmations (the confirmation at 189 is unmatched,
 * F1 18/19) and the swing-low workflow matches all 10, with every offset inside
 * the configured ±12 tolerance window.
 */
@Tag("analysis-demo")
class EventSynchronizationExampleTest {

    @Test
    void demoScoresBothSwingDirections() {
        EventSynchronizationExample.DemoResult demo = EventSynchronizationExample.run();

        assertExact(demo.swingHighs(), 9, 10, 9, 18.0 / 19.0);
        assertExact(demo.swingLows(), 10, 10, 10, 1.0);
        for (Result result : new Result[] { demo.swingHighs(), demo.swingLows() }) {
            assertEquals(result.predictedCount(), result.matchedCount() + result.unmatchedPredictedIndexes().size());
            assertEquals(result.referenceCount(), result.matchedCount() + result.unmatchedReferenceIndexes().size());
            for (org.ta4j.core.analysis.event.EventSynchronizationIndicator.Result.Match match : result.matches()) {
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
        assertTrue(result.precision().doubleValue() > 0.0 && result.precision().doubleValue() <= 1.0);
        assertTrue(result.recall().doubleValue() > 0.0 && result.recall().doubleValue() <= 1.0);
        assertEquals(f1Score, result.f1Score().doubleValue(), 1e-12);
    }
}

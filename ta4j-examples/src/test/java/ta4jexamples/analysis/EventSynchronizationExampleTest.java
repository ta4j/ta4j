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
 * Runs the deterministic demo and verifies that both workflows score the
 * momentum crossings against causal ZigZag confirmation events within the
 * configured tolerance windows.
 */
@Tag("analysis-demo")
class EventSynchronizationExampleTest {

    @Test
    void demoScoresBothSwingDirections() {
        EventSynchronizationExample.DemoResult demo = EventSynchronizationExample.run();

        assertScored(demo.swingHighs());
        assertScored(demo.swingLows());
        for (Result result : new Result[] { demo.swingHighs(), demo.swingLows() }) {
            assertEquals(result.predictedCount(), result.matchedCount() + result.unmatchedPredictedIndexes().size());
            assertEquals(result.referenceCount(), result.matchedCount() + result.unmatchedReferenceIndexes().size());
            for (org.ta4j.core.analysis.event.EventSynchronizationIndicator.Result.Match match : result.matches()) {
                assertTrue(match.offsetBars() >= -12 && match.offsetBars() <= 12,
                        "offset outside the demo tolerance window: " + match);
            }
        }
    }

    private static void assertScored(Result result) {
        assertTrue(result.matchedCount() >= 3, "expected matches, got " + result.matches());
        assertTrue(result.precision().doubleValue() > 0.0 && result.precision().doubleValue() <= 1.0);
        assertTrue(result.recall().doubleValue() > 0.0 && result.recall().doubleValue() <= 1.0);
        assertTrue(result.f1Score().doubleValue() > 0.0 && result.f1Score().doubleValue() <= 1.0);
    }
}

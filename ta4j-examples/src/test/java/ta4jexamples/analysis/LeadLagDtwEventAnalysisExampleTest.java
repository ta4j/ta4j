/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.event.EventMutualInformationResult;

/**
 * Companion regression check for {@link LeadLagDtwEventAnalysisExample}.
 *
 * <p>
 * Runs the deterministic demo over its committed Coinbase BTC daily dataset and
 * smoke-checks all three capabilities: the TLCC profile covers the full
 * {@code [-20, 20]} lag range with best lag 0, the DTW z-score shape distance
 * is defined, and both event-MI evaluations score 110 clamped samples with the
 * recorded positive counts, a positive target rate, and normalized mutual
 * information inside {@code [0, 1]}. Exact numerical parity stays in the owning
 * indicator and evaluator unit tests.
 * </p>
 */
@Tag("analysis-demo")
class LeadLagDtwEventAnalysisExampleTest {

    @Test
    void exampleReportsAllThreeCapabilities() {
        LeadLagDtwEventAnalysisExample.DemoResult demo = LeadLagDtwEventAnalysisExample.run();

        assertEquals(41, demo.profile().points().size());
        assertEquals(41, demo.profile().points().stream().filter(point -> point.isDefined()).count());
        assertEquals(java.util.List.of(0), demo.profile().bestLags());
        assertEquals(OptionalInt.of(0), demo.profile().selectedLag());
        assertTrue(!demo.profile().selectedCorrelation().isNaN());

        assertTrue(!demo.dtwDistance().isNaN());
        assertTrue(demo.dtwDistance().isPositive());

        assertScored(demo.swingHighMi(), 84);
        assertScored(demo.swingLowMi(), 82);
    }

    private static void assertScored(EventMutualInformationResult result, int positives) {
        assertEquals(110, result.sampleCount());
        assertEquals(positives, result.positiveTargetCount());
        assertTrue(result.positiveTargetRate().doubleValue() > 0.0);
        assertTrue(!result.mutualInformationNats().isNaN());
        assertTrue(!result.targetEntropyNats().isNaN());
        double normalized = result.normalizedMutualInformation().doubleValue();
        assertTrue(normalized >= 0.0 && normalized <= 1.0);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.event.EventMutualInformationResult;

/**
 * Companion regression check for {@link LeadLagDtwEventAnalysisExample}.
 *
 * <p>
 * Runs the deterministic demo and verifies that all three capabilities report
 * defined, non-negative findings on the synthetic sine series.
 * </p>
 */
@Tag("analysis-demo")
class LeadLagDtwEventAnalysisExampleTest {

    @Test
    void exampleReportsAllThreeCapabilities() {
        LeadLagDtwEventAnalysisExample.DemoResult demo = LeadLagDtwEventAnalysisExample.run();

        assertFalse(demo.profile().bestLags().isEmpty(), "expected at least one defined lag");
        assertTrue(demo.profile().selectedLag().isPresent());
        assertFalse(demo.profile().selectedCorrelation().isNaN());

        assertTrue(demo.dtwDistance().isPositive() || demo.dtwDistance().isZero(),
                "expected a non-negative DTW distance, got " + demo.dtwDistance());

        assertScored(demo.swingHighMi());
        assertScored(demo.swingLowMi());
    }

    private static void assertScored(EventMutualInformationResult result) {
        assertTrue(result.sampleCount() > 0, "expected samples, got " + result.sampleCount());
        assertFalse(result.mutualInformationNats().isNaN(), "expected defined MI");
        assertFalse(result.targetEntropyNats().isNaN(), "expected defined target entropy");
        int positives = result.positiveTargetCount();
        if (positives > 0 && positives < result.sampleCount()) {
            double normalized = result.normalizedMutualInformation().doubleValue();
            assertTrue(normalized >= 0.0 && normalized <= 1.0 + 1.0e-9, "normalized MI outside [0, 1]: " + normalized);
        } else {
            assertTrue(result.normalizedMutualInformation().isNaN(), "constant target must report NaN normalized MI");
        }
    }
}

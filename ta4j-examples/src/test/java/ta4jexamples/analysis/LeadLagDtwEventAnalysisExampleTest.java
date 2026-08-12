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
 * verifies all three capabilities report exactly: the TLCC profile covers all
 * 41 lags of the [-20, 20] range with best lag 0 at correlation
 * -0.6910846951240238, the DTW z-score shape distance is 1.530179399557817, and
 * both event-MI evaluations score 110 clamped samples with the recorded nats
 * values.
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
        assertEquals(-0.6910846951240238, demo.profile().selectedCorrelation().doubleValue(), 1e-12);

        assertEquals(1.530179399557817, demo.dtwDistance().doubleValue(), 1e-12);

        assertScored(demo.swingHighMi(), 84, 0.04445407127318292, 0.5468519922342628, 0.08129086462967386);
        assertScored(demo.swingLowMi(), 82, 0.1530501888614821, 0.5672739606962849, 0.2697994257900095);
    }

    private static void assertScored(EventMutualInformationResult result, int positives, double mi, double entropy,
            double normalized) {
        assertEquals(110, result.sampleCount());
        assertEquals(positives, result.positiveTargetCount());
        assertEquals(mi, result.mutualInformationNats().doubleValue(), 1e-12);
        assertEquals(entropy, result.targetEntropyNats().doubleValue(), 1e-12);
        assertEquals(normalized, result.normalizedMutualInformation().doubleValue(), 1e-12);
        assertTrue(result.positiveTargetRate().doubleValue() > 0.0);
    }
}

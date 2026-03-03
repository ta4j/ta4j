/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import java.util.Map;

import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;

/**
 * Baseline walk-forward profiles for Elliott analysis.
 *
 * @since 0.22.4
 */
public final class ElliottWaveWalkForwardProfiles {

    private ElliottWaveWalkForwardProfiles() {
    }

    /**
     * Creates the baseline EW walk-forward context used by tuning runs.
     *
     * @param degree base Elliott degree for the runner
     * @return baseline context
     * @since 0.22.4
     */
    public static ElliottWaveWalkForwardContext baseline(ElliottDegree degree) {
        ElliottWaveAnalysisRunner runner = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(1)
                .lowerDegrees(1)
                .maxScenarios(5)
                .minConfidence(0.15)
                .build();
        return new ElliottWaveWalkForwardContext(runner, null, 5, Map.of("profile", "baseline"));
    }
}

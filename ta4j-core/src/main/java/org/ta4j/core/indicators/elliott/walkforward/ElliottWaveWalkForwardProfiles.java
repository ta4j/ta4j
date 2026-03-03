/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import java.util.List;
import java.util.Map;

import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.walkforward.WalkForwardConfig;

/**
 * Baseline walk-forward profiles for Elliott analysis.
 *
 * @since 0.22.4
 */
public final class ElliottWaveWalkForwardProfiles {

    /**
     * Baseline EW degree selected from cross-dataset walk-forward tuning.
     */
    public static final ElliottDegree BASELINE_DEGREE = ElliottDegree.MINUTE;

    /**
     * Baseline split geometry and horizon policy selected from cross-dataset
     * walk-forward tuning.
     */
    public static final WalkForwardConfig BASELINE_CONFIG = WalkForwardConfig.defaultConfig();

    private static final int BASELINE_HIGHER_DEGREES = 2;
    private static final int BASELINE_LOWER_DEGREES = 2;
    private static final int BASELINE_MAX_SCENARIOS = 25;
    private static final double BASELINE_MIN_CONFIDENCE = 0.0;
    private static final int BASELINE_SCENARIO_SWING_WINDOW = 0;
    private static final int BASELINE_FRACTAL_WINDOW = 2;
    private static final int BASELINE_MAX_PREDICTIONS = 5;
    private static final String BASELINE_PROFILE_ID = "baseline-minute-f2-h2l2-max25-sw0";

    private ElliottWaveWalkForwardProfiles() {
    }

    /**
     * Creates the tuned EW baseline walk-forward context.
     *
     * <p>
     * This baseline is fixed for cross-run comparability and uses the tuned
     * degree/detector/swing policy established by the EW walk-forward calibration
     * study.
     *
     * @return tuned baseline context
     * @since 0.22.4
     */
    public static ElliottWaveWalkForwardContext baseline() {
        return baseline(BASELINE_DEGREE);
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
                .higherDegrees(BASELINE_HIGHER_DEGREES)
                .lowerDegrees(BASELINE_LOWER_DEGREES)
                .maxScenarios(BASELINE_MAX_SCENARIOS)
                .minConfidence(BASELINE_MIN_CONFIDENCE)
                .scenarioSwingWindow(BASELINE_SCENARIO_SWING_WINDOW)
                .swingDetector(SwingDetectors.fractal(BASELINE_FRACTAL_WINDOW))
                .swingFilter(swings -> swings == null ? List.of() : List.copyOf(swings))
                .build();
        return new ElliottWaveWalkForwardContext(runner, null, BASELINE_MAX_PREDICTIONS,
                Map.of("profile", BASELINE_PROFILE_ID, "degree", degree.name()));
    }

    /**
     * Returns the tuned baseline split geometry and horizon policy.
     *
     * <p>
     * Use this configuration as a locked baseline for EW walk-forward comparisons.
     *
     * @return tuned baseline walk-forward configuration
     * @since 0.22.4
     */
    public static WalkForwardConfig baselineConfig() {
        return BASELINE_CONFIG;
    }
}

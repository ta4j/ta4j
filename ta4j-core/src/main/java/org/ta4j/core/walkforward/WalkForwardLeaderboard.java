/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.walkforward.calibration.CalibrationSelection;

/**
 * Candidate ranking output from walk-forward tuning.
 *
 * @param <C>                candidate context type
 * @param entries            ranked entries (best first)
 * @param evaluatedCount     total candidates evaluated
 * @param keptCount          number of entries retained in ranking output
 * @param primaryHorizonBars optimization horizon used for ranking
 * @since 0.22.4
 */
public record WalkForwardLeaderboard<C>(List<Entry<C>> entries, int evaluatedCount, int keptCount,
        int primaryHorizonBars) {

    /**
     * Creates a validated leaderboard.
     */
    public WalkForwardLeaderboard {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (evaluatedCount < 0) {
            throw new IllegalArgumentException("evaluatedCount must be >= 0");
        }
        if (keptCount < 0) {
            throw new IllegalArgumentException("keptCount must be >= 0");
        }
        if (primaryHorizonBars <= 0) {
            throw new IllegalArgumentException("primaryHorizonBars must be > 0");
        }
    }

    /**
     * Ranked leaderboard entry.
     *
     * @param <C>                  candidate context type
     * @param candidate            candidate descriptor
     * @param objectiveScore       objective score output
     * @param globalMetrics        primary-horizon global metrics used for scoring
     * @param calibrationSelection calibration selection summary
     * @param runResult            full run result
     * @since 0.22.4
     */
    public record Entry<C>(WalkForwardCandidate<C> candidate, WalkForwardObjective.Score objectiveScore,
            Map<String, Double> globalMetrics, CalibrationSelection calibrationSelection,
            WalkForwardRunResult<?, ?> runResult) {

        /**
         * Creates a validated leaderboard entry.
         */
        public Entry {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(objectiveScore, "objectiveScore");
            globalMetrics = globalMetrics == null ? Map.of() : Map.copyOf(globalMetrics);
            Objects.requireNonNull(calibrationSelection, "calibrationSelection");
            Objects.requireNonNull(runResult, "runResult");
        }
    }
}

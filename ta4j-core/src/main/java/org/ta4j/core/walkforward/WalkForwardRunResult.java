/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.num.Num;

/**
 * Complete output bundle from a walk-forward engine run.
 *
 * @param <P>                    prediction payload type
 * @param <O>                    realized outcome type
 * @param config                 run configuration
 * @param splits                 fold definitions
 * @param snapshots              captured prediction snapshots
 * @param observationsByHorizon  observations grouped by horizon
 * @param globalMetricsByHorizon metric map grouped by horizon then metric name
 * @param foldMetricsByHorizon   metric map grouped by horizon, fold id, and
 *                               metric name
 * @param leakageAudit           leakage audit traces
 * @param runtimeReport          runtime summary
 * @param manifest               reproducibility manifest
 * @since 0.22.4
 */
public record WalkForwardRunResult<P, O>(WalkForwardConfig config, List<WalkForwardSplit> splits,
        List<PredictionSnapshot<P>> snapshots, Map<Integer, List<WalkForwardObservation<P, O>>> observationsByHorizon,
        Map<Integer, Map<String, Num>> globalMetricsByHorizon,
        Map<Integer, Map<String, Map<String, Num>>> foldMetricsByHorizon, List<LeakageAudit> leakageAudit,
        WalkForwardRuntimeReport runtimeReport, WalkForwardExperimentManifest manifest) {

    /**
     * Creates a validated run result.
     */
    public WalkForwardRunResult {
        Objects.requireNonNull(config, "config");
        splits = splits == null ? List.of() : List.copyOf(splits);
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        observationsByHorizon = observationsByHorizon == null ? Map.of() : Map.copyOf(observationsByHorizon);
        globalMetricsByHorizon = globalMetricsByHorizon == null ? Map.of() : Map.copyOf(globalMetricsByHorizon);
        foldMetricsByHorizon = foldMetricsByHorizon == null ? Map.of() : Map.copyOf(foldMetricsByHorizon);
        leakageAudit = leakageAudit == null ? List.of() : List.copyOf(leakageAudit);
        Objects.requireNonNull(runtimeReport, "runtimeReport");
        Objects.requireNonNull(manifest, "manifest");
    }

    /**
     * Gets global metrics for a single horizon.
     *
     * @param horizonBars horizon in bars
     * @return metrics map
     * @since 0.22.4
     */
    public Map<String, Num> globalMetricsForHorizon(int horizonBars) {
        return globalMetricsByHorizon.getOrDefault(horizonBars, Map.of());
    }

    /**
     * Gets fold metrics for a single horizon.
     *
     * @param horizonBars horizon in bars
     * @return fold metric map
     * @since 0.22.4
     */
    public Map<String, Map<String, Num>> foldMetricsForHorizon(int horizonBars) {
        return foldMetricsByHorizon.getOrDefault(horizonBars, Map.of());
    }

    /**
     * @return holdout split, if present
     * @since 0.22.4
     */
    public Optional<WalkForwardSplit> holdoutSplit() {
        return splits.stream().filter(WalkForwardSplit::holdout).findFirst();
    }

    /**
     * Audit record capturing decision and evaluation-window boundaries for leakage
     * checks.
     *
     * @param foldId            fold id
     * @param decisionIndex     prediction decision index
     * @param visibleStartIndex lower bound of visible series for prediction
     * @param visibleEndIndex   upper bound of visible series for prediction
     * @param labelStartIndex   start index of realized label window
     * @param labelEndIndex     end index of realized label window
     * @param horizonBars       horizon in bars
     * @param withinFoldBounds  whether the label window is fully inside the fold
     *                          test
     * @param holdout           whether the snapshot belongs to holdout
     * @param note              diagnostic note
     * @since 0.22.4
     */
    public record LeakageAudit(String foldId, int decisionIndex, int visibleStartIndex, int visibleEndIndex,
            int labelStartIndex, int labelEndIndex, int horizonBars, boolean withinFoldBounds, boolean holdout,
            String note) {
    }
}

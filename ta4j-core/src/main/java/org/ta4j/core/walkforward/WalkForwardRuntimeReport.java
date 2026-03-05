/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Runtime statistics for one walk-forward execution.
 *
 * @param overallRuntime     total runtime
 * @param minFoldRuntime     minimum fold runtime
 * @param maxFoldRuntime     maximum fold runtime
 * @param averageFoldRuntime average fold runtime
 * @param medianFoldRuntime  median fold runtime
 * @param foldRuntimes       per-fold runtime details
 * @since 0.22.4
 */
public record WalkForwardRuntimeReport(Duration overallRuntime, Duration minFoldRuntime, Duration maxFoldRuntime,
        Duration averageFoldRuntime, Duration medianFoldRuntime, List<FoldRuntime> foldRuntimes) {

    /**
     * Creates a validated runtime report.
     */
    public WalkForwardRuntimeReport {
        Objects.requireNonNull(overallRuntime, "overallRuntime");
        Objects.requireNonNull(minFoldRuntime, "minFoldRuntime");
        Objects.requireNonNull(maxFoldRuntime, "maxFoldRuntime");
        Objects.requireNonNull(averageFoldRuntime, "averageFoldRuntime");
        Objects.requireNonNull(medianFoldRuntime, "medianFoldRuntime");
        foldRuntimes = foldRuntimes == null ? List.of() : List.copyOf(foldRuntimes);
    }

    /**
     * @return empty runtime report
     * @since 0.22.4
     */
    public static WalkForwardRuntimeReport empty() {
        return new WalkForwardRuntimeReport(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                List.of());
    }

    /**
     * Per-fold runtime detail.
     *
     * @param foldId        fold id
     * @param runtime       fold runtime
     * @param snapshotCount number of decision snapshots processed in the fold
     * @since 0.22.4
     */
    public record FoldRuntime(String foldId, Duration runtime, int snapshotCount) {

        /**
         * Creates a validated fold runtime.
         */
        public FoldRuntime {
            Objects.requireNonNull(foldId, "foldId");
            Objects.requireNonNull(runtime, "runtime");
            if (snapshotCount < 0) {
                throw new IllegalArgumentException("snapshotCount must be >= 0");
            }
        }
    }
}

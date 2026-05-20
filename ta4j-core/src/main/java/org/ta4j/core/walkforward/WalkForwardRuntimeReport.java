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
        validateNonNegative("overallRuntime", overallRuntime);
        validateNonNegative("minFoldRuntime", minFoldRuntime);
        validateNonNegative("maxFoldRuntime", maxFoldRuntime);
        validateNonNegative("averageFoldRuntime", averageFoldRuntime);
        validateNonNegative("medianFoldRuntime", medianFoldRuntime);
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
     * Per-snapshot runtime detail.
     *
     * @param decisionIndex   decision index processed for the snapshot
     * @param runtime         snapshot runtime
     * @param predictionCount number of ranked predictions retained
     * @since 0.22.4
     */
    public record SnapshotRuntime(int decisionIndex, Duration runtime, int predictionCount) {

        /**
         * Creates a validated snapshot runtime.
         */
        public SnapshotRuntime {
            Objects.requireNonNull(runtime, "runtime");
            if (decisionIndex < 0) {
                throw new IllegalArgumentException("decisionIndex must be >= 0");
            }
            validateNonNegative("runtime", runtime);
            if (predictionCount < 0) {
                throw new IllegalArgumentException("predictionCount must be >= 0");
            }
        }
    }

    /**
     * Per-fold runtime detail.
     *
     * @param foldId                 fold id
     * @param runtime                fold runtime
     * @param snapshotCount          number of decision snapshots processed in the
     *                               fold
     * @param minSnapshotRuntime     minimum snapshot runtime in the fold
     * @param maxSnapshotRuntime     maximum snapshot runtime in the fold
     * @param averageSnapshotRuntime average snapshot runtime in the fold
     * @param medianSnapshotRuntime  median snapshot runtime in the fold
     * @param snapshotRuntimes       per-snapshot runtime details in decision order
     * @since 0.22.4
     */
    public record FoldRuntime(String foldId, Duration runtime, int snapshotCount, Duration minSnapshotRuntime,
            Duration maxSnapshotRuntime, Duration averageSnapshotRuntime, Duration medianSnapshotRuntime,
            List<SnapshotRuntime> snapshotRuntimes) {

        /**
         * Creates a fold runtime with empty snapshot timing detail.
         *
         * @since 0.22.4
         */
        public FoldRuntime(String foldId, Duration runtime, int snapshotCount) {
            this(foldId, runtime, snapshotCount, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, List.of());
        }

        /**
         * Creates a validated fold runtime.
         */
        public FoldRuntime {
            Objects.requireNonNull(foldId, "foldId");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(minSnapshotRuntime, "minSnapshotRuntime");
            Objects.requireNonNull(maxSnapshotRuntime, "maxSnapshotRuntime");
            Objects.requireNonNull(averageSnapshotRuntime, "averageSnapshotRuntime");
            Objects.requireNonNull(medianSnapshotRuntime, "medianSnapshotRuntime");
            validateNonNegative("runtime", runtime);
            validateNonNegative("minSnapshotRuntime", minSnapshotRuntime);
            validateNonNegative("maxSnapshotRuntime", maxSnapshotRuntime);
            validateNonNegative("averageSnapshotRuntime", averageSnapshotRuntime);
            validateNonNegative("medianSnapshotRuntime", medianSnapshotRuntime);
            if (snapshotCount < 0) {
                throw new IllegalArgumentException("snapshotCount must be >= 0");
            }
            snapshotRuntimes = snapshotRuntimes == null ? List.of() : List.copyOf(snapshotRuntimes);
            if (!snapshotRuntimes.isEmpty() && snapshotRuntimes.size() != snapshotCount) {
                throw new IllegalArgumentException("snapshotRuntimes size must match snapshotCount");
            }
            if (minSnapshotRuntime.compareTo(maxSnapshotRuntime) > 0) {
                throw new IllegalArgumentException("minSnapshotRuntime must be <= maxSnapshotRuntime");
            }
            if (snapshotCount > 0) {
                validateWithinBounds("averageSnapshotRuntime", averageSnapshotRuntime, minSnapshotRuntime,
                        maxSnapshotRuntime);
                validateWithinBounds("medianSnapshotRuntime", medianSnapshotRuntime, minSnapshotRuntime,
                        maxSnapshotRuntime);
            }
        }
    }

    private static void validateNonNegative(String name, Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    private static void validateWithinBounds(String name, Duration duration, Duration min, Duration max) {
        if (duration.compareTo(min) < 0 || duration.compareTo(max) > 0) {
            throw new IllegalArgumentException(name + " must stay within min/max snapshot runtime bounds");
        }
    }
}

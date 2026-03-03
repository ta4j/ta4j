/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration for walk-forward execution and tuning.
 *
 * <p>
 * The configuration defines fold geometry, optimization and reporting horizons,
 * ranking depth, holdout behavior, and deterministic seed values.
 *
 * <p>
 * For tuning governance and fair candidate comparison, choose one baseline
 * configuration for a given study and keep it fixed across all candidate runs.
 * You can still inject a custom configuration, but changing fold geometry or
 * horizon settings between runs invalidates direct metric comparability.
 *
 * @param minTrainBars       minimum number of bars required in each training
 *                           prefix
 * @param testBars           number of bars in each test fold
 * @param stepBars           index step used to advance to the next fold
 * @param purgeBars          bars excluded before each test fold
 * @param embargoBars        bars excluded between train and test boundaries
 * @param holdoutBars        number of trailing bars reserved for holdout
 * @param primaryHorizonBars primary optimization horizon in bars
 * @param reportingHorizons  additional reporting horizons in bars
 * @param optimizationTopK   ranking depth used for optimization
 * @param reportingTopKs     additional ranking depths for reporting
 * @param seed               deterministic seed for candidate ordering and
 *                           reproducibility
 * @since 0.22.4
 */
public record WalkForwardConfig(int minTrainBars, int testBars, int stepBars, int purgeBars, int embargoBars,
        int holdoutBars, int primaryHorizonBars, List<Integer> reportingHorizons, int optimizationTopK,
        List<Integer> reportingTopKs, long seed) {

    /**
     * Creates a validated walk-forward configuration.
     */
    public WalkForwardConfig {
        if (minTrainBars <= 0) {
            throw new IllegalArgumentException("minTrainBars must be > 0");
        }
        if (testBars <= 0) {
            throw new IllegalArgumentException("testBars must be > 0");
        }
        if (stepBars <= 0) {
            throw new IllegalArgumentException("stepBars must be > 0");
        }
        if (purgeBars < 0) {
            throw new IllegalArgumentException("purgeBars must be >= 0");
        }
        if (embargoBars < 0) {
            throw new IllegalArgumentException("embargoBars must be >= 0");
        }
        if (holdoutBars < 0) {
            throw new IllegalArgumentException("holdoutBars must be >= 0");
        }
        if (primaryHorizonBars <= 0) {
            throw new IllegalArgumentException("primaryHorizonBars must be > 0");
        }
        if (optimizationTopK <= 0) {
            throw new IllegalArgumentException("optimizationTopK must be > 0");
        }

        reportingHorizons = normalizePositiveIntegers(reportingHorizons, "reportingHorizons");
        reportingTopKs = normalizePositiveIntegers(reportingTopKs, "reportingTopKs");
    }

    /**
     * Creates the default global walk-forward configuration.
     *
     * <p>
     * Defaults match the approved PRD policy: primary horizon {@code H=60},
     * reporting horizons {@code 30/150}, optimize at {@code k=3}, report
     * {@code k=1/5}.
     *
     * <p>
     * Treat these defaults as a baseline profile for a comparison cycle. If you
     * override them, keep your chosen overrides fixed for the full cycle.
     *
     * @return default configuration
     * @since 0.22.4
     */
    public static WalkForwardConfig defaultConfig() {
        return new WalkForwardConfig(252, 60, 30, 5, 5, 150, 60, List.of(30, 150), 3, List.of(1, 5), 42L);
    }

    /**
     * @return all horizons (primary first, then additional unique reporting
     *         horizons)
     * @since 0.22.4
     */
    public List<Integer> allHorizons() {
        Set<Integer> ordered = new LinkedHashSet<>();
        ordered.add(primaryHorizonBars);
        ordered.addAll(reportingHorizons);
        return List.copyOf(ordered);
    }

    /**
     * @return all ranking depths ({@code k}) used for optimization and reporting
     * @since 0.22.4
     */
    public List<Integer> allTopKs() {
        Set<Integer> ordered = new LinkedHashSet<>();
        ordered.add(optimizationTopK);
        ordered.addAll(reportingTopKs);
        return List.copyOf(ordered);
    }

    /**
     * Creates a deterministic configuration hash useful for manifests.
     *
     * @return stable hash string for this configuration
     * @since 0.22.4
     */
    public String configHash() {
        String canonical = minTrainBars + "|" + testBars + "|" + stepBars + "|" + purgeBars + "|" + embargoBars + "|"
                + holdoutBars + "|" + primaryHorizonBars + "|" + reportingHorizons + "|" + optimizationTopK + "|"
                + reportingTopKs + "|" + seed;
        return Integer.toHexString(canonical.hashCode());
    }

    private static List<Integer> normalizePositiveIntegers(List<Integer> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Integer> normalized = new ArrayList<>();
        for (Integer value : values) {
            Objects.requireNonNull(value, fieldName + " must not contain null values");
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " values must be > 0");
            }
            normalized.add(value);
        }
        return List.copyOf(new LinkedHashSet<>(normalized));
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.BarSeries;

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

    private static final int DEFAULT_MIN_TRAIN_BARS = 120;
    private static final int DEFAULT_TEST_BARS = 40;
    private static final int DEFAULT_STEP_BARS = 20;
    private static final int DEFAULT_PURGE_BARS = 1;
    private static final int DEFAULT_EMBARGO_BARS = 1;
    private static final int DEFAULT_HOLDOUT_BARS = 40;
    private static final int DEFAULT_PRIMARY_HORIZON_BARS = 15;
    private static final List<Integer> DEFAULT_REPORTING_HORIZONS = List.of(7, 30);
    private static final int DEFAULT_OPTIMIZATION_TOP_K = 3;
    private static final List<Integer> DEFAULT_REPORTING_TOP_KS = List.of(1, 5);
    private static final long DEFAULT_SEED = 42L;

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
     * Creates a walk-forward configuration auto-derived from the supplied series.
     *
     * <p>
     * This constructor applies series-size heuristics intended for general-purpose
     * walk-forward studies. The generated geometry is deterministic for a given
     * series length.
     *
     * @param series input series used to derive geometry and horizons
     * @since 0.22.4
     */
    public WalkForwardConfig(BarSeries series) {
        this(deriveForSeries(series));
    }

    /**
     * Creates the default global walk-forward configuration.
     *
     * <p>
     * These defaults are intentionally general-purpose and not tied to any
     * asset-specific tuning profile.
     *
     * @return default configuration
     * @since 0.22.4
     */
    public static WalkForwardConfig defaultConfig() {
        return new WalkForwardConfig(DEFAULT_MIN_TRAIN_BARS, DEFAULT_TEST_BARS, DEFAULT_STEP_BARS, DEFAULT_PURGE_BARS,
                DEFAULT_EMBARGO_BARS, DEFAULT_HOLDOUT_BARS, DEFAULT_PRIMARY_HORIZON_BARS, DEFAULT_REPORTING_HORIZONS,
                DEFAULT_OPTIMIZATION_TOP_K, DEFAULT_REPORTING_TOP_KS, DEFAULT_SEED);
    }

    /**
     * Creates a series-aware default walk-forward configuration.
     *
     * <p>
     * The generated values are derived from bar-count heuristics: approximately 60%
     * training bars, 15% test bars, 10% holdout bars, and horizon depths sized from
     * test bars.
     *
     * <p>
     * Treat the generated configuration as locked for a comparison cycle. If you
     * rerun candidate studies, avoid changing generated values run-to-run for the
     * same dataset.
     *
     * @param series input series used to derive geometry and horizons
     * @return derived configuration
     * @since 0.22.4
     */
    public static WalkForwardConfig defaultConfig(BarSeries series) {
        return new WalkForwardConfig(deriveForSeries(series));
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

    private WalkForwardConfig(AutoDerivedConfig derived) {
        this(derived.minTrainBars(), derived.testBars(), derived.stepBars(), derived.purgeBars(), derived.embargoBars(),
                derived.holdoutBars(), derived.primaryHorizonBars(), derived.reportingHorizons(),
                derived.optimizationTopK(), derived.reportingTopKs(), derived.seed());
    }

    private static AutoDerivedConfig deriveForSeries(BarSeries series) {
        Objects.requireNonNull(series, "series");
        int totalBars = series.getBarCount();
        if (totalBars <= 0) {
            throw new IllegalArgumentException("series must contain at least one bar");
        }

        int holdoutBars = clamp(totalBars / 10, 0, Math.max(0, totalBars / 4));
        int purgeBars = totalBars >= 200 ? 2 : totalBars >= 80 ? 1 : 0;
        int embargoBars = purgeBars;

        int evaluationBars = Math.max(1, totalBars - holdoutBars);
        int maxTrainBars = Math.max(1, evaluationBars - purgeBars - embargoBars - 1);
        int minTrainBars = clamp((evaluationBars * 3) / 5, 20, maxTrainBars);

        int maxTestBars = Math.max(1, evaluationBars - minTrainBars - purgeBars - embargoBars);
        int testBars = clamp(evaluationBars / 6, 10, maxTestBars);
        int stepBars = Math.max(1, testBars / 2);

        int primaryHorizonBars = clamp(testBars / 3, 5, testBars);
        int shortHorizon = Math.max(1, primaryHorizonBars / 2);
        int longHorizon = Math.min(testBars, Math.max(1, primaryHorizonBars * 2));
        List<Integer> reportingHorizons = normalizePositiveIntegers(List.of(shortHorizon, longHorizon),
                "reportingHorizons").stream().filter(value -> value != primaryHorizonBars).toList();

        return new AutoDerivedConfig(minTrainBars, testBars, stepBars, purgeBars, embargoBars, holdoutBars,
                primaryHorizonBars, reportingHorizons, DEFAULT_OPTIMIZATION_TOP_K, DEFAULT_REPORTING_TOP_KS,
                DEFAULT_SEED);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private record AutoDerivedConfig(int minTrainBars, int testBars, int stepBars, int purgeBars, int embargoBars,
            int holdoutBars, int primaryHorizonBars, List<Integer> reportingHorizons, int optimizationTopK,
            List<Integer> reportingTopKs, long seed) {
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

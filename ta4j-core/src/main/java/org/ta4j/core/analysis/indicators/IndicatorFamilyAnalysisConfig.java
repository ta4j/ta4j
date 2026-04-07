/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.util.Locale;
import java.util.Objects;

/**
 * Configures an indicator family analysis run.
 *
 * @param configId            stable configuration identifier
 * @param correlationWindow   lookback window used for pairwise similarity
 *                            scoring
 * @param similarityThreshold minimum score to consider two indicators in the
 *                            same family
 * @param scoringMode         similarity scoring policy
 * @since 0.22.7
 */
public record IndicatorFamilyAnalysisConfig(String configId, int correlationWindow, double similarityThreshold,
        SimilarityMode scoringMode) {

    private static final int DEFAULT_CORRELATION_WINDOW = 120;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.93;

    /**
     * Creates a validated analysis configuration.
     */
    public IndicatorFamilyAnalysisConfig {
        Objects.requireNonNull(configId, "configId");
        if (configId.isBlank()) {
            throw new IllegalArgumentException("configId must not be blank");
        }
        if (correlationWindow <= 0) {
            throw new IllegalArgumentException("correlationWindow must be > 0");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(scoringMode, "scoringMode");
    }

    /**
     * Returns a default v1 analysis configuration focused on absolute correlations
     * for grouping.
     *
     * @param configId stable configuration identifier
     * @return v1 configuration
     * @since 0.22.7
     */
    public static IndicatorFamilyAnalysisConfig defaultMode(String configId) {
        return new IndicatorFamilyAnalysisConfig(configId, DEFAULT_CORRELATION_WINDOW, DEFAULT_SIMILARITY_THRESHOLD,
                SimilarityMode.ABSOLUTE);
    }

    /**
     * Returns a signed mode tuned for directional clustering.
     *
     * @param configId stable configuration identifier
     * @return directional configuration
     * @since 0.22.7
     */
    public static IndicatorFamilyAnalysisConfig signedMode(String configId) {
        return new IndicatorFamilyAnalysisConfig(configId, DEFAULT_CORRELATION_WINDOW, DEFAULT_SIMILARITY_THRESHOLD,
                SimilarityMode.SIGNED);
    }

    /**
     * Returns a correlation window identifier that can be used for deterministic
     * artifacts.
     *
     * @return normalized config signature
     * @since 0.22.7
     */
    public String signature() {
        return String.format(Locale.ROOT, "%s|%d|%.4f|%s", configId, correlationWindow, similarityThreshold,
                scoringMode.id());
    }

    /**
     * Scoring modes that transform correlation into a family compatibility score.
     *
     * @since 0.22.7
     */
    public enum SimilarityMode {
        /** Uses absolute correlation to group anti-correlated indicators together. */
        ABSOLUTE("absolute") {
            @Override
            public double score(double correlation, int overlapBars, int correlationWindow) {
                return clamp(Math.abs(correlation));
            }
        },
        /** Preserves correlation sign for directional clustering. */
        SIGNED("signed") {
            @Override
            public double score(double correlation, int overlapBars, int correlationWindow) {
                return clamp(correlation);
            }
        };

        private final String id;

        SimilarityMode(String id) {
            this.id = id;
        }

        /**
         * Scores a pair of indicators as a family compatibility value in
         * {@code [-1,1]}.
         *
         * @param correlation       raw correlation in {@code [-1,1]}
         * @param overlapBars       overlapping stable bars used for the score
         * @param correlationWindow reference correlation window
         * @return scored correlation
         * @since 0.22.7
         */
        public abstract double score(double correlation, int overlapBars, int correlationWindow);

        /**
         * @return stable mode id
         * @since 0.22.7
         */
        public String id() {
            return id;
        }

        private static double clamp(double value) {
            if (value < -1.0) {
                return -1.0;
            }
            if (value > 1.0) {
                return 1.0;
            }
            return value;
        }
    }
}

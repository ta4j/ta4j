/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable output from {@link IndicatorFamilyManager}.
 *
 * @param similarityThreshold threshold used for family grouping
 * @param stableIndex         first index where all pairwise correlation scores
 *                            are stable
 * @param familyByIndicator   indicator name to family id mapping
 * @param families            grouped indicator families in deterministic order
 * @param pairSimilarities    pairwise absolute average correlation scores in
 *                            deterministic order
 * @since 0.22.7
 */
public record IndicatorFamilyResult(double similarityThreshold, int stableIndex, Map<String, String> familyByIndicator,
        List<Family> families, List<PairSimilarity> pairSimilarities) {

    /**
     * Creates a validated result.
     *
     * @since 0.22.7
     */
    public IndicatorFamilyResult {
        if (!Double.isFinite(similarityThreshold) || similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
        if (stableIndex < 0) {
            throw new IllegalArgumentException("stableIndex must be >= 0");
        }
        familyByIndicator = immutableLinkedMap(familyByIndicator);
        if (families == null || families.isEmpty()) {
            throw new IllegalArgumentException("families must not be empty");
        }
        families = List.copyOf(families);
        pairSimilarities = pairSimilarities == null ? List.of() : List.copyOf(pairSimilarities);
    }

    private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<K, V> ordered = new LinkedHashMap<>(source.size());
        for (Map.Entry<K, V> entry : source.entrySet()) {
            ordered.put(Objects.requireNonNull(entry.getKey(), "map keys must not be null"),
                    Objects.requireNonNull(entry.getValue(), "map values must not be null"));
        }
        return Collections.unmodifiableMap(ordered);
    }

    /**
     * One indicator family.
     *
     * @param familyId       deterministic family identifier
     * @param indicatorNames indicator names in caller-provided order
     * @since 0.22.7
     */
    public record Family(String familyId, List<String> indicatorNames) {

        /**
         * Creates a validated family.
         *
         * @since 0.22.7
         */
        public Family {
            Objects.requireNonNull(familyId, "familyId");
            if (familyId.isBlank()) {
                throw new IllegalArgumentException("familyId must not be blank");
            }
            if (indicatorNames == null || indicatorNames.isEmpty()) {
                throw new IllegalArgumentException("indicatorNames must not be empty");
            }
            for (String indicatorName : indicatorNames) {
                if (indicatorName == null || indicatorName.isBlank()) {
                    throw new IllegalArgumentException("indicatorNames must not contain null or blank entries");
                }
            }
            indicatorNames = List.copyOf(indicatorNames);
        }
    }

    /**
     * Absolute average correlation score for one indicator pair.
     *
     * @param firstIndicatorName  first indicator name
     * @param secondIndicatorName second indicator name
     * @param similarity          absolute average correlation score in
     *                            {@code [0, 1]}
     * @since 0.22.7
     */
    public record PairSimilarity(String firstIndicatorName, String secondIndicatorName, double similarity) {

        /**
         * Creates a validated pair-similarity record.
         *
         * @since 0.22.7
         */
        public PairSimilarity {
            Objects.requireNonNull(firstIndicatorName, "firstIndicatorName");
            Objects.requireNonNull(secondIndicatorName, "secondIndicatorName");
            if (firstIndicatorName.isBlank() || secondIndicatorName.isBlank()) {
                throw new IllegalArgumentException("indicator names must not be blank");
            }
            if (!Double.isFinite(similarity) || similarity < 0.0 || similarity > 1.0) {
                throw new IllegalArgumentException("similarity must be between 0.0 and 1.0");
            }
        }
    }
}

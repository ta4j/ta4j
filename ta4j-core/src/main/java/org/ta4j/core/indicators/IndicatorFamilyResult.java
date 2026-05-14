/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Immutable output from {@link IndicatorFamilyManager}.
 *
 * @param similarityThreshold threshold used for family grouping
 * @param stableIndex         first index where all pairwise correlation scores
 *                            are stable
 * @param familyByIndicator   indicator name to family id mapping
 * @param families            grouped indicator families with cohesion metrics
 *                            in deterministic order
 * @param pairSimilarities    pairwise similarity metrics in deterministic order
 * @since 0.22.7
 */
public record IndicatorFamilyResult(Num similarityThreshold, int stableIndex, Map<String, String> familyByIndicator,
        List<Family> families, List<PairSimilarity> pairSimilarities) {

    /**
     * Creates a validated result.
     *
     * @since 0.22.7
     */
    public IndicatorFamilyResult {
        similarityThreshold = requireUnitInterval(similarityThreshold, "similarityThreshold");
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

    private static Num requireUnitInterval(Num value, String argumentName) {
        Objects.requireNonNull(value, argumentName);
        Num zero = value.getNumFactory().zero();
        Num one = value.getNumFactory().one();
        if (IndicatorUtils.isInvalid(value) || value.isLessThan(zero) || value.isGreaterThan(one)) {
            throw new IllegalArgumentException(argumentName + " must be between 0.0 and 1.0");
        }
        return value;
    }

    /**
     * One indicator family.
     *
     * @param familyId                    deterministic family identifier
     * @param indicatorNames              indicator names in caller-provided order
     * @param representativeIndicatorName family member with the highest average
     *                                    internal absolute similarity
     * @param averageInternalSimilarity   average absolute similarity for all pairs
     *                                    inside this family; singleton families use
     *                                    {@code 1}
     * @param minimumInternalSimilarity   weakest absolute similarity for any pair
     *                                    inside this family; singleton families use
     *                                    {@code 1}
     * @since 0.22.7
     */
    public record Family(String familyId, List<String> indicatorNames, String representativeIndicatorName,
            Num averageInternalSimilarity, Num minimumInternalSimilarity) {

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
            Objects.requireNonNull(representativeIndicatorName, "representativeIndicatorName");
            if (representativeIndicatorName.isBlank()) {
                throw new IllegalArgumentException("representativeIndicatorName must not be blank");
            }
            if (!indicatorNames.contains(representativeIndicatorName)) {
                throw new IllegalArgumentException("representativeIndicatorName must be a family member");
            }
            averageInternalSimilarity = requireUnitInterval(averageInternalSimilarity, "averageInternalSimilarity");
            minimumInternalSimilarity = requireUnitInterval(minimumInternalSimilarity, "minimumInternalSimilarity");
            indicatorNames = List.copyOf(indicatorNames);
        }
    }

    /**
     * Similarity metrics for one indicator pair.
     *
     * @param firstIndicatorName      first indicator name
     * @param secondIndicatorName     second indicator name
     * @param similarity              absolute average similarity in {@code [0, 1]}
     *                                used for family grouping
     * @param signedAverageSimilarity signed average similarity in {@code [-1, 1]}
     * @param latestSignedSimilarity  most recent valid signed similarity sample in
     *                                {@code [-1, 1]}
     * @param sampleCount             number of valid samples used for the aggregate
     *                                metrics
     * @param minimumSignedSimilarity weakest signed similarity sample in
     *                                {@code [-1, 1]}
     * @param maximumSignedSimilarity strongest signed similarity sample in
     *                                {@code [-1, 1]}
     * @since 0.22.7
     */
    public record PairSimilarity(String firstIndicatorName, String secondIndicatorName, Num similarity,
            Num signedAverageSimilarity, Num latestSignedSimilarity, int sampleCount, Num minimumSignedSimilarity,
            Num maximumSignedSimilarity) {

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
            similarity = requireUnitInterval(similarity, "similarity");
            signedAverageSimilarity = requireSignedUnitInterval(signedAverageSimilarity, "signedAverageSimilarity");
            latestSignedSimilarity = requireSignedUnitInterval(latestSignedSimilarity, "latestSignedSimilarity");
            if (sampleCount < 0) {
                throw new IllegalArgumentException("sampleCount must be >= 0");
            }
            minimumSignedSimilarity = requireSignedUnitInterval(minimumSignedSimilarity, "minimumSignedSimilarity");
            maximumSignedSimilarity = requireSignedUnitInterval(maximumSignedSimilarity, "maximumSignedSimilarity");
            if (minimumSignedSimilarity.isGreaterThan(maximumSignedSimilarity)) {
                throw new IllegalArgumentException("minimumSignedSimilarity must be <= maximumSignedSimilarity");
            }
        }
    }

    private static Num requireSignedUnitInterval(Num value, String argumentName) {
        Objects.requireNonNull(value, argumentName);
        Num minusOne = value.getNumFactory().minusOne();
        Num one = value.getNumFactory().one();
        if (IndicatorUtils.isInvalid(value) || value.isLessThan(minusOne) || value.isGreaterThan(one)) {
            throw new IllegalArgumentException(argumentName + " must be between -1.0 and 1.0");
        }
        return value;
    }
}

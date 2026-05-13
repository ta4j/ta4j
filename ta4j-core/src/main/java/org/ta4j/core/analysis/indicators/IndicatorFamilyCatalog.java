/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable output bundle from one analysis configuration.
 *
 * @param catalogId           deterministic catalog identifier
 * @param manifestId          manifest identifier
 * @param manifestHash        manifest configuration hash
 * @param config              analysis configuration used
 * @param stableIndex         first index with stable data across all indicators
 * @param familyByIndicator   indicator id to family id mapping
 * @param families            family descriptors
 * @param pairSimilarity      similarity by pair key in deterministic order
 * @param pairwiseFingerprint deterministic fingerprint of similarity map
 * @since 0.22.7
 */
public record IndicatorFamilyCatalog(String catalogId, String manifestId, String manifestHash,
        IndicatorFamilyAnalysisConfig config, int stableIndex, Map<String, String> familyByIndicator,
        List<Family> families, Map<String, Double> pairSimilarity, String pairwiseFingerprint) {

    /**
     * Creates a validated catalog.
     */
    public IndicatorFamilyCatalog {
        Objects.requireNonNull(catalogId, "catalogId");
        if (catalogId.isBlank()) {
            throw new IllegalArgumentException("catalogId must not be blank");
        }
        Objects.requireNonNull(manifestId, "manifestId");
        if (manifestId.isBlank()) {
            throw new IllegalArgumentException("manifestId must not be blank");
        }
        Objects.requireNonNull(manifestHash, "manifestHash");
        if (manifestHash.isBlank()) {
            throw new IllegalArgumentException("manifestHash must not be blank");
        }
        Objects.requireNonNull(config, "config");
        if (stableIndex < 0) {
            throw new IllegalArgumentException("stableIndex must be >= 0");
        }
        familyByIndicator = immutableLinkedMap(familyByIndicator);
        families = families == null ? List.of() : List.copyOf(families);
        pairSimilarity = immutableLinkedMap(pairSimilarity);
        if (pairwiseFingerprint == null || pairwiseFingerprint.isBlank()) {
            throw new IllegalArgumentException("pairwiseFingerprint must not be blank");
        }
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
     * Returns the number of families in this catalog.
     *
     * @return family count
     * @since 0.22.7
     */
    public int familyCount() {
        return families.size();
    }

    /**
     * Selects ranked indicators and optionally enforces a maximum of one indicator
     * per family.
     *
     * @param rankedIndicatorIds ranked indicator ids, ordered by preference
     * @param maxCount           maximum indicators to return
     * @param enforceFamilyLimit true to reject duplicate-family picks
     * @return deterministic selected ids
     * @since 0.22.7
     */
    public List<String> select(List<String> rankedIndicatorIds, int maxCount, boolean enforceFamilyLimit) {
        Objects.requireNonNull(rankedIndicatorIds, "rankedIndicatorIds");
        if (maxCount < 0) {
            throw new IllegalArgumentException("maxCount must be >= 0");
        }
        if (maxCount == 0 || rankedIndicatorIds.isEmpty()) {
            return List.of();
        }

        List<String> selected = new ArrayList<>(Math.min(maxCount, rankedIndicatorIds.size()));
        Set<String> usedFamilies = new HashSet<>();
        Set<String> usedIndicators = new HashSet<>();
        for (String indicatorId : rankedIndicatorIds) {
            if (selected.size() >= maxCount) {
                break;
            }
            if (indicatorId == null || indicatorId.isBlank()) {
                throw new IllegalArgumentException("rankedIndicatorIds must not contain null or blank entries");
            }
            if (!usedIndicators.add(indicatorId)) {
                continue;
            }

            String familyId = familyByIndicator.get(indicatorId);
            if (!enforceFamilyLimit || familyId == null || usedFamilies.add(familyId)) {
                selected.add(indicatorId);
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Selects ranked indicators without family deduplication.
     *
     * @param rankedIndicatorIds ranked indicator ids, ordered by preference
     * @param maxCount           maximum indicators to return
     * @return deterministic selected ids
     * @since 0.22.7
     */
    public List<String> select(List<String> rankedIndicatorIds, int maxCount) {
        return select(rankedIndicatorIds, maxCount, false);
    }

    /**
     * Family result with members sorted in stable order.
     *
     * @param familyId     stable family id
     * @param indicatorIds member ids
     * @since 0.22.7
     */
    public record Family(String familyId, List<String> indicatorIds) {
        public Family {
            Objects.requireNonNull(familyId, "familyId");
            if (familyId.isBlank()) {
                throw new IllegalArgumentException("familyId must not be blank");
            }
            if (indicatorIds == null || indicatorIds.isEmpty()) {
                throw new IllegalArgumentException("indicatorIds must not be empty");
            }
            for (String indicatorId : indicatorIds) {
                if (indicatorId == null || indicatorId.isBlank()) {
                    throw new IllegalArgumentException("indicatorIds must not contain null or blank entries");
                }
            }
            indicatorIds = List.copyOf(indicatorIds);
        }
    }

    /**
     * Drift report between two catalogs.
     *
     * @param fromConfigId source config id
     * @param toConfigId   target config id
     * @param changedCount count of moved indicators
     * @param changes      per-indicator family transitions
     * @since 0.22.7
     */
    public record FamilyDrift(String fromConfigId, String toConfigId, int changedCount,
            List<FamilyTransition> changes) {
        public FamilyDrift {
            Objects.requireNonNull(fromConfigId, "fromConfigId");
            Objects.requireNonNull(toConfigId, "toConfigId");
            if (fromConfigId.isBlank() || toConfigId.isBlank()) {
                throw new IllegalArgumentException("config ids must not be blank");
            }
            if (changedCount < 0) {
                throw new IllegalArgumentException("changedCount must be >= 0");
            }
            changes = changes == null ? List.of() : List.copyOf(changes);
            if (changedCount != changes.size()) {
                throw new IllegalArgumentException("changedCount must match changes size");
            }
        }
    }

    /**
     * One indicator family transition.
     *
     * @param indicatorId indicator id
     * @param fromFamily  previous family
     * @param toFamily    next family
     * @since 0.22.7
     */
    public record FamilyTransition(String indicatorId, String fromFamily, String toFamily) {
        public FamilyTransition {
            Objects.requireNonNull(indicatorId, "indicatorId");
            Objects.requireNonNull(fromFamily, "fromFamily");
            Objects.requireNonNull(toFamily, "toFamily");
            if (indicatorId.isBlank() || fromFamily.isBlank() || toFamily.isBlank()) {
                throw new IllegalArgumentException("values must not be blank");
            }
        }
    }
}

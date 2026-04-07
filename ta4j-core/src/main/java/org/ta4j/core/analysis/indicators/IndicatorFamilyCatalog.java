/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        familyByIndicator = familyByIndicator == null ? Map.of() : Map.copyOf(familyByIndicator);
        families = families == null ? List.of() : List.copyOf(families);
        pairSimilarity = pairSimilarity == null ? Map.of() : Map.copyOf(pairSimilarity);
        if (pairwiseFingerprint == null || pairwiseFingerprint.isBlank()) {
            throw new IllegalArgumentException("pairwiseFingerprint must not be blank");
        }
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

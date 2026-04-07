/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.util.List;
import java.util.Objects;

/**
 * Result set for one manifest run across one or more analysis configurations.
 *
 * @param manifestId   manifest identifier
 * @param manifestHash manifest hash
 * @param catalogs     catalogs produced per config
 * @param drifts       optional family drifts between sequential configs
 * @since 0.22.7
 */
public record IndicatorFamilyAnalysisResult(String manifestId, String manifestHash,
        List<IndicatorFamilyCatalog> catalogs, List<IndicatorFamilyCatalog.FamilyDrift> drifts) {

    /**
     * Creates a validated analysis result.
     */
    public IndicatorFamilyAnalysisResult {
        Objects.requireNonNull(manifestId, "manifestId");
        if (manifestId.isBlank()) {
            throw new IllegalArgumentException("manifestId must not be blank");
        }
        Objects.requireNonNull(manifestHash, "manifestHash");
        if (manifestHash.isBlank()) {
            throw new IllegalArgumentException("manifestHash must not be blank");
        }
        catalogs = catalogs == null ? List.of() : List.copyOf(catalogs);
        drifts = drifts == null ? List.of() : List.copyOf(drifts);
    }
}

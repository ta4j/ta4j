/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stable experiment manifest for reproducible walk-forward runs.
 *
 * @param datasetId   dataset identifier
 * @param candidateId candidate identifier
 * @param configHash  deterministic configuration hash
 * @param seed        deterministic seed
 * @param metadata    optional metadata values
 * @since 0.22.4
 */
public record WalkForwardExperimentManifest(String datasetId, String candidateId, String configHash, long seed,
        Map<String, String> metadata) {

    /**
     * Creates a validated experiment manifest.
     */
    public WalkForwardExperimentManifest {
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(configHash, "configHash");
        if (datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId must not be blank");
        }
        if (candidateId.isBlank()) {
            throw new IllegalArgumentException("candidateId must not be blank");
        }
        if (configHash.isBlank()) {
            throw new IllegalArgumentException("configHash must not be blank");
        }
        metadata = metadata == null || metadata.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(metadata));
    }
}

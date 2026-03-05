/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prediction capture at one decision index.
 *
 * @param <P>            prediction payload type
 * @param foldId         decision fold id
 * @param decisionIndex  decision index in the root series
 * @param topPredictions ranked predictions captured for this decision
 * @param metadata       optional metadata and audit tags
 * @since 0.22.4
 */
public record PredictionSnapshot<P>(String foldId, int decisionIndex, List<RankedPrediction<P>> topPredictions,
        Map<String, String> metadata) {

    /**
     * Creates a validated prediction snapshot.
     */
    public PredictionSnapshot {
        Objects.requireNonNull(foldId, "foldId");
        if (foldId.isBlank()) {
            throw new IllegalArgumentException("foldId must not be blank");
        }
        if (decisionIndex < 0) {
            throw new IllegalArgumentException("decisionIndex must be >= 0");
        }
        topPredictions = topPredictions == null ? List.of() : List.copyOf(topPredictions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * @return stable snapshot key for grouping observations
     * @since 0.22.4
     */
    public String snapshotKey() {
        return foldId + "@" + decisionIndex;
    }
}

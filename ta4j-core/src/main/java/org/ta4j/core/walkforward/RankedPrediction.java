/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.Objects;

/**
 * Ranked prediction candidate with probability and confidence values.
 *
 * @param <P>          payload type
 * @param predictionId stable prediction identifier
 * @param rank         1-based ranking position (lower is better)
 * @param probability  calibrated or raw probability score in {@code [0,1]}
 * @param confidence   confidence score in {@code [0,1]}
 * @param payload      domain payload
 * @since 0.22.4
 */
public record RankedPrediction<P>(String predictionId, int rank, double probability, double confidence, P payload) {

    /**
     * Creates a validated ranked prediction.
     */
    public RankedPrediction {
        Objects.requireNonNull(predictionId, "predictionId");
        if (predictionId.isBlank()) {
            throw new IllegalArgumentException("predictionId must not be blank");
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be > 0");
        }
        validateUnitInterval("probability", probability);
        validateUnitInterval("confidence", confidence);
    }

    /**
     * Returns a copy with probability replaced.
     *
     * @param calibratedProbability calibrated probability
     * @return copied prediction
     * @since 0.22.4
     */
    public RankedPrediction<P> withProbability(double calibratedProbability) {
        return new RankedPrediction<>(predictionId, rank, calibratedProbability, confidence, payload);
    }

    private static void validateUnitInterval(String field, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be in [0.0, 1.0]");
        }
    }
}

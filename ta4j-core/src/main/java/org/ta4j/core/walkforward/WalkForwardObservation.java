/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.Objects;

/**
 * Joined prediction-outcome observation for one horizon.
 *
 * @param <P>             prediction payload type
 * @param <O>             realized outcome type
 * @param snapshot        prediction snapshot metadata
 * @param prediction      ranked prediction evaluated at this horizon
 * @param realizedOutcome realized outcome for the prediction
 * @param horizonBars     horizon used for labeling
 * @since 0.22.4
 */
public record WalkForwardObservation<P, O>(PredictionSnapshot<P> snapshot, RankedPrediction<P> prediction,
        O realizedOutcome, int horizonBars) {

    /**
     * Creates a validated observation.
     */
    public WalkForwardObservation {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(prediction, "prediction");
        if (horizonBars <= 0) {
            throw new IllegalArgumentException("horizonBars must be > 0");
        }
    }

    /**
     * @return fold id from the originating snapshot
     * @since 0.22.4
     */
    public String foldId() {
        return snapshot.foldId();
    }
}

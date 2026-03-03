/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import org.ta4j.core.BarSeries;

/**
 * Computes realized outcomes for predictions at a fixed horizon.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
@FunctionalInterface
public interface OutcomeLabeler<P, O> {

    /**
     * Builds realized outcome for one prediction.
     *
     * @param fullSeries    full input series
     * @param decisionIndex decision index
     * @param horizonBars   horizon in bars
     * @param prediction    ranked prediction to evaluate
     * @return realized outcome
     * @since 0.22.4
     */
    O label(BarSeries fullSeries, int decisionIndex, int horizonBars, RankedPrediction<P> prediction);
}

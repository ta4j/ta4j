/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;

import org.ta4j.core.BarSeries;

/**
 * Produces ranked predictions for a decision index.
 *
 * @param <C> provider context type
 * @param <P> prediction payload type
 * @since 0.22.4
 */
@FunctionalInterface
public interface PredictionProvider<C, P> {

    /**
     * Generates ranked predictions for the supplied decision index.
     *
     * @param fullSeries    full input series
     * @param decisionIndex decision index to evaluate
     * @param context       provider context
     * @return ranked predictions (best rank first)
     * @since 0.22.4
     */
    List<RankedPrediction<P>> predict(BarSeries fullSeries, int decisionIndex, C context);
}

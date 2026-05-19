/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import org.ta4j.core.num.Num;

/**
 * Scores one decoded strategy-search candidate.
 *
 * <p>
 * Evaluators should stay deterministic for the same candidate and inputs so GA
 * runs remain reproducible under a fixed seed.
 *
 * @param <C> candidate context type
 * @since 0.22.7
 */
@FunctionalInterface
public interface StrategyFitnessEvaluator<C> {

    /**
     * Computes the fitness score for one decoded candidate.
     *
     * @param candidate decoded candidate context
     * @return fitness score; {@code null} or {@code NaN} demotes the candidate to
     *         the bottom of the ranking
     * @since 0.22.7
     */
    Num evaluate(C candidate);
}

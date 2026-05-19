/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Genetic-algorithm utilities for parameterized strategy search.
 *
 * <p>
 * The initial surface stays parameter-first: explicit
 * {@link org.ta4j.core.optimization.ga.ParameterDomain} definitions feed a
 * {@link org.ta4j.core.optimization.ga.StrategyChromosomeCodec}, which in turn
 * powers {@link org.ta4j.core.optimization.ga.StrategyChromosome} and
 * {@link org.ta4j.core.optimization.ga.GeneticStrategyTuner} runs.
 *
 * @since 0.22.7
 */
package org.ta4j.core.optimization.ga;

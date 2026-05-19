/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Genetic-algorithm utilities for parameterized candidate search.
 *
 * <p>
 * The initial surface stays parameter-first: explicit
 * {@link org.ta4j.core.optimization.ga.ParameterDomain} definitions feed a
 * {@link org.ta4j.core.optimization.ga.CandidateCodec}, which in turn powers
 * {@link org.ta4j.core.optimization.ga.CandidateChromosome} and
 * {@link org.ta4j.core.optimization.ga.GeneticCandidateSearch} runs. Indicator
 * families can opt in through
 * {@link org.ta4j.core.optimization.ga.IndicatorCandidateSpec} without changing
 * the core indicator inheritance model.
 *
 * @since 0.22.7
 */
package org.ta4j.core.optimization.ga;

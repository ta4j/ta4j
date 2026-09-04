/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Swappable Monte Carlo sampling techniques shared by the forecast indicators.
 * <p>
 * A {@link org.ta4j.core.analysis.montecarlo.MonteCarloMethod} owns only
 * terminal-sample generation: it draws randomness exclusively from the
 * {@link org.ta4j.core.analysis.montecarlo.MonteCarloContext#random() context
 * random generator} and returns cumulative terminal returns, or {@code null}
 * when the technique cannot produce a usable sample set. The calling engine in
 * {@code org.ta4j.core.indicators.forecast} keeps gating, lookback-window
 * construction, seeding, quantiles, and price mapping. Samples produced by a
 * foreign {@code NumFactory} are coerced to the series factory before
 * summarization.
 * <p>
 * The stock recursive shock-path scheme lives in
 * {@link org.ta4j.core.analysis.montecarlo.ShockPathMonteCarloMethod}, and the
 * conjugate Bayesian alternative in
 * {@link org.ta4j.core.analysis.montecarlo.NormalInverseGammaForecastMethod}.
 * <p>
 * The public {@code MonteCarloMethod} seam is accelerator-neutral. Internally,
 * the package-private
 * {@link org.ta4j.core.analysis.montecarlo.MonteCarloOperationGraphs} lowers
 * explicitly supported built-in method graphs into a canonical primitive
 * {@link org.ta4j.core.analysis.montecarlo.MonteCarloOperation} description
 * (type, version, parameters, children) that seeds the future operation-level
 * native acceleration ABI; graphs containing custom or unknown techniques
 * decline lowering and remain valid on the scalar path. This surface is not
 * part of the public API.
 *
 * @since 0.24.2
 */
package org.ta4j.core.analysis.montecarlo;

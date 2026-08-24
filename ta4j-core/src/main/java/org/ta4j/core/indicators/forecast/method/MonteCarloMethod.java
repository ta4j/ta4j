/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.method;

import java.util.List;

import org.ta4j.core.num.Num;

/**
 * Swappable Monte Carlo technique producing terminal cumulative log-return
 * samples for one forecast.
 *
 * <p>
 * Implementations own only sample generation. Stability gating, historical
 * window assembly, deterministic seeding, terminal value mapping, and forecast
 * quantile assembly remain with the calling simulation engine, so every method
 * shares identical observable semantics.
 *
 * <p>
 * Contract:
 *
 * <ul>
 * <li>Return exactly {@link MonteCarloContext#iterationCount()} samples drawn
 * from the context's random generator only.</li>
 * <li>Every sample must be a finite cumulative log return over
 * {@link MonteCarloContext#horizon()} bars.</li>
 * <li>Return {@code null} when no stable result can be produced; the engine
 * maps this to an unstable forecast.</li>
 * </ul>
 *
 * @see ShockPathMonteCarloMethod
 * @see NormalInverseGammaForecastMethod
 * @since 0.24.2
 */
@FunctionalInterface
public interface MonteCarloMethod {

    /**
     * Generates terminal cumulative log-return samples for one decision index.
     *
     * @param context validated simulation inputs including the seeded random
     *                generator
     * @return exactly {@code context.iterationCount()} finite cumulative log-return
     *         samples, or {@code null} when no stable result can be produced
     */
    List<Num> terminalReturns(MonteCarloContext context);
}

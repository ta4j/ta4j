/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

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
 * <p>
 * Techniques compose fluently, in the same style as
 * {@link org.ta4j.core.Rule#and(org.ta4j.core.Rule) Rule.and}: each decorator
 * returns a new method honoring the same contract, propagates an unstable inner
 * result, and declares the forecast unstable when the inner technique breaks
 * the contract. For example:
 *
 * <pre>{@code
 * MonteCarloMethod method = new ShockPathMonteCarloMethod(ShockModel.HISTORICAL_BOOTSTRAP, VolatilityUpdateMode.EWMA,
 *         0.94).pooledWith(NormalInverseGammaForecastMethod.withEmpiricalPriors().overSmoothedResiduals())
 *         .widenedByRecentVolatility()
 *         .withStudentTScaleMixing();
 * }</pre>
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
     * @since 0.24.2
     */
    List<Num> terminalReturns(MonteCarloContext context);

    /**
     * Returns a 50/50 mixture of this technique and {@code other}.
     *
     * <p>
     * This technique draws the leading {@code iterationCount / 2} samples and
     * {@code other} the remainder; the pooled list concatenates them in that order.
     * Each component runs on its own generator seeded from two consecutive
     * {@code nextLong()} draws of the context generator, so component draws never
     * interleave and each component reproduces its standalone draws at the reduced
     * count. Iteration counts below 2 yield an unstable result.
     *
     * @param other technique pooled with this one
     * @return pooled technique
     * @since 0.26.1
     */
    default MonteCarloMethod pooledWith(MonteCarloMethod other) {
        return new EnsembleMonteCarloMethod(this, other);
    }

    /**
     * Returns this technique widened when recent realized volatility exceeds the
     * state volatility, using a 10-bar recent window and a widening factor capped
     * at 4.
     *
     * @return widened technique
     * @see #widenedByRecentVolatility(int, double)
     * @since 0.26.1
     */
    default MonteCarloMethod widenedByRecentVolatility() {
        return new RecentVolatilityWideningMonteCarloMethod(this);
    }

    /**
     * Returns this technique widened when recent realized volatility exceeds the
     * state volatility.
     *
     * <p>
     * Samples are rescaled around their own mean:
     *
     * <pre>
     * factor = min(maxWiden, max(1, recentRealizedVol / stateVol))
     * path   = mean(samples) + factor * (sample - mean(samples))
     * </pre>
     *
     * where {@code recentRealizedVol} is the RMS of the trailing
     * {@code recentBarCount} historical log returns (the whole lookback window when
     * shorter). Only dispersion changes, never location, and calm regimes
     * ({@code factor = 1}) are returned unchanged. The widening is deterministic in
     * the window and draws no randomness.
     *
     * @param recentBarCount trailing window length in bars, must be at least 2
     * @param maxWiden       upper bound on the widening factor, must be a finite
     *                       value &gt;= 1
     * @return widened technique
     * @since 0.26.1
     */
    default MonteCarloMethod widenedByRecentVolatility(int recentBarCount, double maxWiden) {
        return new RecentVolatilityWideningMonteCarloMethod(this, recentBarCount, maxWiden);
    }

    /**
     * Returns this technique with Student-t tails of 5 degrees of freedom.
     *
     * @return tail-mixed technique
     * @see #withStudentTScaleMixing(int)
     * @since 0.26.1
     */
    default MonteCarloMethod withStudentTScaleMixing() {
        return new StudentTScaleMixingMonteCarloMethod(this);
    }

    /**
     * Returns this technique with Student-t tails obtained by scale mixing.
     *
     * <p>
     * Each sample is rescaled around the drift path by an independent mean-one
     * factor {@code f = sqrt(df / chiSq(df)) / E[sqrt(df / chiSq(df))]}:
     *
     * <pre>
     * path = h * drift + f * (sample - h * drift)
     * </pre>
     *
     * A gaussian inner technique becomes a Student-t technique with the given
     * degrees of freedom, up to the mean-one normalization. Lower degrees of
     * freedom produce heavier tails; large values approach the unchanged inner
     * technique. One chi-square draw per sample is taken from the context generator
     * after the inner technique completes.
     *
     * @param degreesOfFreedom mixing degrees of freedom, must be &gt;= 2
     * @return tail-mixed technique
     * @since 0.26.1
     */
    default MonteCarloMethod withStudentTScaleMixing(int degreesOfFreedom) {
        return new StudentTScaleMixingMonteCarloMethod(this, degreesOfFreedom);
    }
}

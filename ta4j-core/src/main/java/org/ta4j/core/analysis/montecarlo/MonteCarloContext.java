/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Prepared inputs handed to a {@link MonteCarloMethod} for one decision index.
 *
 * <p>
 * The simulation engine validates stability, assembles the historical
 * log-return window, and seeds the random generator deterministically before
 * invoking a method. Implementations must draw all randomness exclusively from
 * {@link #random()} -- or, when {@link #perPathRandoms()} is present, from
 * {@link #randomForPath(int)} per simulated path -- so that equal seeds
 * reproduce equal forecasts independently of path execution order.
 *
 * @param index                decision bar index of the forecast
 * @param horizon              positive forecast horizon in bars
 * @param iterationCount       exact number of terminal samples the method must
 *                             return
 * @param historicalLogReturns finite log-return window ending at {@code index};
 *                             size equals the configured lookback bar count
 * @param moments              stable canonical log-return moments at
 *                             {@code index}
 * @param random               deterministic seeded random generator
 * @param numFactory           number factory of the underlying bar series
 * @param perPathRandoms       optional engine-provided factory of independent
 *                             per-path streams; {@code null} selects the shared
 *                             sequential stream in {@code random}
 */
public record MonteCarloContext(int index, int horizon, int iterationCount, List<Num> historicalLogReturns,
        ReturnMoments moments, RandomGenerator random, NumFactory numFactory,
        IntFunction<RandomGenerator> perPathRandoms) {

    public MonteCarloContext {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (horizon < 1 || iterationCount < 1) {
            throw new IllegalArgumentException("horizon and iterationCount must be >= 1");
        }
        historicalLogReturns = Objects.requireNonNull(historicalLogReturns, "historicalLogReturns must not be null");
        moments = Objects.requireNonNull(moments, "moments must not be null");
        random = Objects.requireNonNull(random, "random must not be null");
        numFactory = Objects.requireNonNull(numFactory, "numFactory must not be null");
    }

    /**
     * Creates a context without per-path streams: the method draws all randomness
     * sequentially from {@code random}.
     *
     * @param index                decision bar index of the forecast
     * @param horizon              positive forecast horizon in bars
     * @param iterationCount       exact number of terminal samples the method must
     *                             return
     * @param historicalLogReturns finite log-return window ending at {@code index}
     * @param moments              stable canonical log-return moments at
     *                             {@code index}
     * @param random               deterministic seeded random generator
     * @param numFactory           number factory of the underlying bar series
     * @since 0.24.2
     */
    public MonteCarloContext(int index, int horizon, int iterationCount, List<Num> historicalLogReturns,
            ReturnMoments moments, RandomGenerator random, NumFactory numFactory) {
        this(index, horizon, iterationCount, historicalLogReturns, moments, random, numFactory, null);
    }

    /**
     * Returns the random generator a method must consume for simulated path
     * {@code pathIndex}: an independent engine-seeded stream when per-path streams
     * are present, otherwise the shared sequential stream. Drawing each path's
     * randomness exclusively from this accessor keeps forecasts reproducible
     * regardless of path execution order.
     *
     * @param pathIndex zero-based index of the simulated path
     * @return the deterministic random generator for that path
     * @since 0.24.2
     */
    public RandomGenerator randomForPath(int pathIndex) {
        return perPathRandoms != null ? perPathRandoms.apply(pathIndex) : random;
    }

    /**
     * Returns the engine-owned lookback log-return window handed to the method for
     * this invocation.
     *
     * @return the engine-owned historical log-return window, read-only by contract
     * @since 0.24.2
     */
    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The window is engine-owned per invocation and "
            + "handed to the method read-only; copying it per bar would defeat the allocation budget of hot "
            + "forecast paths.")
    public List<Num> historicalLogReturns() {
        return historicalLogReturns;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.List;
import java.util.Objects;
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
 * {@link #random()} so that equal seeds reproduce equal forecasts.
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
 * @since 0.24.2
 */
public record MonteCarloContext(int index, int horizon, int iterationCount, List<Num> historicalLogReturns,
        ReturnMoments moments, RandomGenerator random, NumFactory numFactory) {

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

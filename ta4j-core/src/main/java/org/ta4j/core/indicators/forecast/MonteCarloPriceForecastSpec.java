/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.num.Num;

/**
 * Read-only Monte Carlo price forecast configuration for optional batch
 * acceleration adapters.
 *
 * <p>
 * This type exposes exactly the state an adapter needs to validate and snapshot
 * a supported forecast graph. It is not an executable graph IR and does not
 * weaken the scalar {@link MonteCarloPriceForecastIndicator#getValue(int)}
 * contract.
 *
 * @param priceIndicator        price source
 * @param stateIndicator        log-return state source
 * @param horizon               forecast horizon in bars
 * @param iterationCount        simulated terminal path count
 * @param lookbackBarCount      historical return lookback
 * @param seed                  deterministic base seed
 * @param shockModel            shock source
 * @param volatilityUpdateMode  within-path volatility behavior
 * @param volatilityDecayFactor EWMA decay for within-path volatility updates
 * @param quantileProbabilities summarized quantile probabilities
 * @since 0.23.1
 */
public record MonteCarloPriceForecastSpec(Indicator<Num> priceIndicator,
        ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, int horizon, int iterationCount,
        int lookbackBarCount, long seed, MonteCarloReturnProjectionIndicator.ShockModel shockModel,
        MonteCarloReturnProjectionIndicator.VolatilityUpdateMode volatilityUpdateMode, double volatilityDecayFactor,
        List<Double> quantileProbabilities) {

    /**
     * Version of the deterministic per-path sampling contract used by scalar and
     * native implementations.
     *
     * @since 0.23.1
     */
    public static final int RNG_VERSION = 1;

    public MonteCarloPriceForecastSpec {
        Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        Objects.requireNonNull(stateIndicator, "stateIndicator must not be null");
        if (horizon < 1 || iterationCount < 1 || lookbackBarCount < 1) {
            throw new IllegalArgumentException("horizon, iterationCount, and lookbackBarCount must be >= 1");
        }
        Objects.requireNonNull(shockModel, "shockModel must not be null");
        Objects.requireNonNull(volatilityUpdateMode, "volatilityUpdateMode must not be null");
        if (Double.isNaN(volatilityDecayFactor) || volatilityDecayFactor <= 0d || volatilityDecayFactor >= 1d) {
            throw new IllegalArgumentException("volatilityDecayFactor must be in (0, 1)");
        }
        List<Double> checkedQuantileProbabilities = Objects.requireNonNull(quantileProbabilities,
                "quantileProbabilities must not be null");
        if (checkedQuantileProbabilities.isEmpty()) {
            throw new IllegalArgumentException("quantileProbabilities must not be empty");
        }
        for (Double probability : checkedQuantileProbabilities) {
            Double value = Objects.requireNonNull(probability, "quantile probability must not be null");
            if (Double.isNaN(value) || value < 0d || value > 1d) {
                throw new IllegalArgumentException("quantile probability must be in [0, 1]");
            }
        }
        quantileProbabilities = List.copyOf(checkedQuantileProbabilities);
    }
}

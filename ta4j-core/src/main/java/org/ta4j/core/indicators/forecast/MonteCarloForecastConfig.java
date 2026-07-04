/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Configuration for Monte Carlo return forecast indicators.
 *
 * @param horizon               forecast horizon in bars
 * @param iterationCount        number of simulated paths
 * @param lookbackBarCount      number of historical returns used for empirical
 *                              shocks
 * @param seed                  base random seed
 * @param shockModel            shock model
 * @param volatilityUpdateMode  volatility behavior inside each path
 * @param volatilityDecayFactor EWMA decay used when volatility updates are
 *                              enabled
 * @param quantileProbabilities quantiles to include in returned distributions
 * @since 0.22.9
 */
public record MonteCarloForecastConfig(int horizon, int iterationCount, int lookbackBarCount, long seed,
        ShockModel shockModel, VolatilityUpdateMode volatilityUpdateMode, double volatilityDecayFactor,
        List<Double> quantileProbabilities) {

    /**
     * Creates a Monte Carlo forecast configuration.
     *
     * @since 0.22.9
     */
    public MonteCarloForecastConfig {
        if (horizon < 1) {
            throw new IllegalArgumentException("horizon must be >= 1");
        }
        if (iterationCount < 1) {
            throw new IllegalArgumentException("iterationCount must be >= 1");
        }
        if (lookbackBarCount < 1) {
            throw new IllegalArgumentException("lookbackBarCount must be >= 1");
        }
        shockModel = Objects.requireNonNull(shockModel, "shockModel must not be null");
        volatilityUpdateMode = Objects.requireNonNull(volatilityUpdateMode, "volatilityUpdateMode must not be null");
        if (Double.isNaN(volatilityDecayFactor) || volatilityDecayFactor <= 0d || volatilityDecayFactor >= 1d) {
            throw new IllegalArgumentException("volatilityDecayFactor must be in (0, 1)");
        }
        quantileProbabilities = validateQuantiles(quantileProbabilities);
    }

    /**
     * Returns a defensive quantile probability list copy.
     *
     * @return quantile probabilities
     * @since 0.22.9
     */
    @Override
    public List<Double> quantileProbabilities() {
        return Collections.unmodifiableList(new ArrayList<>(quantileProbabilities));
    }

    /**
     * Returns a builder with default values.
     *
     * @return builder
     * @since 0.22.9
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MonteCarloForecastConfig}.
     *
     * @since 0.22.9
     */
    public static final class Builder {

        private int horizon = 1;
        private int iterationCount = 1_000;
        private int lookbackBarCount = 252;
        private long seed = 42L;
        private ShockModel shockModel = ShockModel.STANDARDIZED_EMPIRICAL;
        private VolatilityUpdateMode volatilityUpdateMode = VolatilityUpdateMode.CONSTANT;
        private double volatilityDecayFactor = 0.94d;
        private List<Double> quantileProbabilities = ForecastDistribution.DEFAULT_QUANTILE_PROBABILITIES;

        private Builder() {
        }

        /**
         * Sets the forecast horizon.
         *
         * @param horizon forecast horizon in bars
         * @return this builder
         * @since 0.22.9
         */
        public Builder horizon(int horizon) {
            this.horizon = horizon;
            return this;
        }

        /**
         * Sets the number of simulated paths.
         *
         * @param iterationCount iteration count
         * @return this builder
         * @since 0.22.9
         */
        public Builder iterationCount(int iterationCount) {
            this.iterationCount = iterationCount;
            return this;
        }

        /**
         * Sets the empirical lookback count.
         *
         * @param lookbackBarCount lookback count
         * @return this builder
         * @since 0.22.9
         */
        public Builder lookbackBarCount(int lookbackBarCount) {
            this.lookbackBarCount = lookbackBarCount;
            return this;
        }

        /**
         * Sets the base random seed.
         *
         * @param seed base random seed
         * @return this builder
         * @since 0.22.9
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets the shock model.
         *
         * @param shockModel shock model
         * @return this builder
         * @since 0.22.9
         */
        public Builder shockModel(ShockModel shockModel) {
            this.shockModel = shockModel;
            return this;
        }

        /**
         * Sets the volatility update mode.
         *
         * @param volatilityUpdateMode volatility update mode
         * @return this builder
         * @since 0.22.9
         */
        public Builder volatilityUpdateMode(VolatilityUpdateMode volatilityUpdateMode) {
            this.volatilityUpdateMode = volatilityUpdateMode;
            return this;
        }

        /**
         * Sets the volatility EWMA decay factor.
         *
         * @param volatilityDecayFactor decay factor in {@code (0, 1)}
         * @return this builder
         * @since 0.22.9
         */
        public Builder volatilityDecayFactor(double volatilityDecayFactor) {
            this.volatilityDecayFactor = volatilityDecayFactor;
            return this;
        }

        /**
         * Sets the quantiles to include in returned distributions.
         *
         * @param probabilities quantile probabilities in {@code [0, 1]}
         * @return this builder
         * @since 0.22.9
         */
        public Builder quantiles(double... probabilities) {
            Objects.requireNonNull(probabilities, "probabilities must not be null");
            Double[] boxed = new Double[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                boxed[i] = probabilities[i];
            }
            this.quantileProbabilities = List.of(boxed);
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return configuration
         * @since 0.22.9
         */
        public MonteCarloForecastConfig build() {
            return new MonteCarloForecastConfig(horizon, iterationCount, lookbackBarCount, seed, shockModel,
                    volatilityUpdateMode, volatilityDecayFactor, quantileProbabilities);
        }
    }

    private static List<Double> validateQuantiles(List<Double> quantileProbabilities) {
        List<Double> input = Objects.requireNonNull(quantileProbabilities, "quantileProbabilities must not be null");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("quantileProbabilities must not be empty");
        }
        TreeSet<Double> sorted = new TreeSet<>();
        for (Double probability : input) {
            Double value = Objects.requireNonNull(probability, "quantile probability must not be null");
            if (Double.isNaN(value) || value < 0d || value > 1d) {
                throw new IllegalArgumentException("quantile probability must be in [0, 1]");
            }
            sorted.add(value);
        }
        return List.copyOf(sorted);
    }
}

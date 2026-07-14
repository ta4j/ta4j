/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;

/**
 * Monte Carlo cumulative log-return forecast indicator.
 *
 * @since 0.22.9
 */
public final class MonteCarloReturnProjectionIndicator extends CachedIndicator<Forecast>
        implements ReturnForecastProjectionIndicator {

    private final MonteCarloSimulation simulation;

    /** Creates a one-bar projection with default settings. */
    public MonteCarloReturnProjectionIndicator(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        this(stateIndicator, 1);
    }

    /** Creates a projection with default settings for the requested horizon. */
    public MonteCarloReturnProjectionIndicator(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator,
            int horizon) {
        this(builder(stateIndicator).horizon(horizon));
    }

    private MonteCarloReturnProjectionIndicator(Builder builder) {
        super(builder.stateIndicator);
        this.simulation = new MonteCarloSimulation(builder.stateIndicator, builder.settings());
    }

    /**
     * Returns an advanced-settings builder.
     *
     * @param stateIndicator return-moment state source
     * @return Monte Carlo builder
     * @since 0.23.1
     */
    public static Builder builder(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        return new Builder(stateIndicator);
    }

    @Override
    protected Forecast calculate(int index) {
        return simulation.project(index, value -> value);
    }

    @Override
    public int getCountOfUnstableBars() {
        return simulation.getCountOfUnstableBars();
    }

    @Override
    public int getHorizon() {
        return simulation.getHorizon();
    }

    @Override
    public ReturnRepresentation getReturnRepresentation() {
        return ReturnRepresentation.LOG;
    }

    /** Shock source for simulated paths. */
    public enum ShockModel {
        /** Sample raw historical returns. */
        HISTORICAL_BOOTSTRAP,
        /** Sample standardized empirical residuals. */
        STANDARDIZED_EMPIRICAL,
        /** Sample standard normal shocks. */
        NORMAL
    }

    /** Volatility behavior inside simulated paths. */
    public enum VolatilityUpdateMode {
        /** Keep volatility fixed. */
        CONSTANT,
        /** Update variance by EWMA after each simulated step. */
        EWMA
    }

    /** Builder for advanced Monte Carlo settings. */
    public static final class Builder {

        private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
        private int horizon = 1;
        private int iterationCount = 1_000;
        private int lookbackBarCount = 252;
        private long seed = 42L;
        private ShockModel shockModel = ShockModel.STANDARDIZED_EMPIRICAL;
        private VolatilityUpdateMode volatilityUpdateMode = VolatilityUpdateMode.CONSTANT;
        private double volatilityDecayFactor = 0.94d;
        private List<Double> quantileProbabilities = Forecast.DEFAULT_QUANTILE_PROBABILITIES;

        private Builder(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
            this.stateIndicator = Objects.requireNonNull(stateIndicator, "stateIndicator must not be null");
        }

        /** Sets the positive forecast horizon in bars. */
        public Builder horizon(int value) {
            horizon = value;
            return this;
        }

        /** Sets the positive number of simulated terminal paths. */
        public Builder iterationCount(int value) {
            iterationCount = value;
            return this;
        }

        /** Sets the positive historical-return lookback. */
        public Builder lookbackBarCount(int value) {
            lookbackBarCount = value;
            return this;
        }

        /** Sets the deterministic base seed. */
        public Builder seed(long value) {
            seed = value;
            return this;
        }

        /** Sets the simulated shock source. */
        public Builder shockModel(ShockModel value) {
            shockModel = value;
            return this;
        }

        /** Sets within-path volatility behavior. */
        public Builder volatilityUpdateMode(VolatilityUpdateMode value) {
            volatilityUpdateMode = value;
            return this;
        }

        /** Sets the EWMA decay used by within-path volatility updates. */
        public Builder volatilityDecayFactor(double value) {
            volatilityDecayFactor = value;
            return this;
        }

        /** Sets the quantile probabilities summarized from terminal paths. */
        public Builder quantiles(double... probabilities) {
            Objects.requireNonNull(probabilities, "probabilities must not be null");
            Double[] boxed = new Double[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                boxed[i] = probabilities[i];
            }
            quantileProbabilities = List.of(boxed);
            return this;
        }

        /** Builds the validated projection indicator. */
        public MonteCarloReturnProjectionIndicator build() {
            return new MonteCarloReturnProjectionIndicator(this);
        }

        private MonteCarloSettings settings() {
            return new MonteCarloSettings(horizon, iterationCount, lookbackBarCount, seed, shockModel,
                    volatilityUpdateMode, volatilityDecayFactor, quantileProbabilities);
        }
    }
}

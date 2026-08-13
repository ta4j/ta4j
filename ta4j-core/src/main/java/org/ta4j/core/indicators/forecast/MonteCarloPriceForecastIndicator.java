/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Exact Monte Carlo terminal-price forecast indicator.
 *
 * <p>
 * Each cumulative log-return path is converted to its terminal price before the
 * empirical distribution is summarized, so every returned moment and quantile
 * describes the same transformed paths.
 *
 * @since 0.22.9
 */
public final class MonteCarloPriceForecastIndicator extends CachedIndicator<Forecast>
        implements ForecastProjectionIndicator {

    private static final int MAX_EXPONENT = 700;

    private final Indicator<Num> priceIndicator;
    private final MonteCarloSimulation simulation;

    /**
     * Creates a one-bar forecast and infers price from {@link LogReturnIndicator}.
     *
     * @param stateIndicator log-return moment state source
     * @since 0.22.9
     */
    public MonteCarloPriceForecastIndicator(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        this(stateIndicator, 1);
    }

    /**
     * Creates a forecast and infers price from {@link LogReturnIndicator}.
     *
     * @param stateIndicator log-return moment state source
     * @param horizon        positive forecast horizon in bars
     * @since 0.22.9
     */
    public MonteCarloPriceForecastIndicator(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator,
            int horizon) {
        this(builder(stateIndicator).horizon(horizon));
    }

    /**
     * Creates a one-bar forecast with an explicit price source.
     *
     * @param priceIndicator price source
     * @param stateIndicator log-return moment state source
     * @since 0.23.1
     */
    public MonteCarloPriceForecastIndicator(Indicator<Num> priceIndicator,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        this(priceIndicator, stateIndicator, 1);
    }

    /**
     * Creates a forecast with an explicit price source and horizon.
     *
     * @param priceIndicator price source
     * @param stateIndicator log-return moment state source
     * @param horizon        positive forecast horizon in bars
     * @since 0.23.1
     */
    public MonteCarloPriceForecastIndicator(Indicator<Num> priceIndicator,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, int horizon) {
        this(builder(priceIndicator, stateIndicator).horizon(horizon));
    }

    private MonteCarloPriceForecastIndicator(Builder builder) {
        super(IndicatorUtils.requireSameSeries(builder.priceIndicator, builder.stateIndicator));
        IndicatorUtils.requireSameSeries(builder.stateIndicator.getReturnIndicator(), builder.stateIndicator);
        this.priceIndicator = builder.priceIndicator;
        this.simulation = new MonteCarloSimulation(builder.stateIndicator, builder.settings());
    }

    /**
     * Returns a builder that infers the price source.
     *
     * @param stateIndicator log-return moment state source
     * @return exact price projection builder
     * @since 0.23.1
     */
    public static Builder builder(ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        return new Builder(sourceIndicator(stateIndicator), stateIndicator);
    }

    /**
     * Returns a builder with an explicit price source.
     *
     * @param priceIndicator price source
     * @param stateIndicator log-return moment state source
     * @return exact price projection builder
     * @since 0.23.1
     */
    public static Builder builder(Indicator<Num> priceIndicator,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        return new Builder(priceIndicator, stateIndicator);
    }

    @Override
    protected Forecast calculate(int index) {
        Num price = priceIndicator.getValue(index);
        if (!Num.isFinite(price) || !price.isPositive()) {
            return Forecast.unstable(index, getHorizon());
        }
        NumFactory numFactory = price.getNumFactory();
        Num exponentLimit = numFactory.numOf(MAX_EXPONENT);
        return simulation.project(index, cumulativeReturn -> {
            Num normalizedReturn = numFactory.numOf(cumulativeReturn.bigDecimalValue());
            if (!Num.isFinite(normalizedReturn) || normalizedReturn.isZero() && !cumulativeReturn.isZero()
                    || normalizedReturn.abs().isGreaterThan(exponentLimit)) {
                return null;
            }
            Num growth = normalizedReturn.exp();
            Num terminalPrice = price.multipliedBy(growth);
            return terminalPrice.isZero() && !growth.isZero() ? null : terminalPrice;
        });
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public Forecast getValue(int index) {
        if (index >= 0 && index < getBarSeries().getRemovedBarsCount()) {
            return Forecast.unstable(index, getHorizon());
        }
        return super.getValue(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(priceIndicator.getCountOfUnstableBars(), simulation.getCountOfUnstableBars());
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getHorizon() {
        return simulation.getHorizon();
    }

    private static Indicator<Num> sourceIndicator(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        ReturnForecastStateIndicator<? extends ReturnMomentState> validated = validateStateIndicator(stateIndicator);
        ReturnIndicator returnIndicator = validated.getReturnIndicator();
        if (returnIndicator instanceof LogReturnIndicator logReturns) {
            return logReturns.getSourceIndicator();
        }
        throw new IllegalArgumentException("stateIndicator must use a LogReturnIndicator to infer the price source");
    }

    private static ReturnForecastStateIndicator<? extends ReturnMomentState> validateStateIndicator(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
        ReturnForecastStateIndicator<? extends ReturnMomentState> validated = Objects.requireNonNull(stateIndicator,
                "stateIndicator must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("stateIndicator must use ReturnRepresentation.LOG");
        }
        return validated;
    }

    /**
     * Builder for advanced exact price simulations.
     *
     * @since 0.23.1
     */
    public static final class Builder {

        private final Indicator<Num> priceIndicator;
        private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
        private int horizon = 1;
        private int iterationCount = 1_000;
        private int lookbackBarCount = 252;
        private long seed = 42L;
        private MonteCarloReturnProjectionIndicator.ShockModel shockModel = MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL;
        private MonteCarloReturnProjectionIndicator.VolatilityUpdateMode volatilityUpdateMode = MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT;
        private double volatilityDecayFactor = 0.94d;
        private List<Double> quantileProbabilities = Forecast.DEFAULT_QUANTILE_PROBABILITIES;

        private Builder(Indicator<Num> priceIndicator,
                ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator) {
            this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
            this.stateIndicator = validateStateIndicator(stateIndicator);
        }

        /**
         * Sets the positive forecast horizon in bars.
         *
         * @param value horizon in bars
         * @return this builder
         * @since 0.23.1
         */
        public Builder horizon(int value) {
            horizon = value;
            return this;
        }

        /**
         * Sets the positive number of simulated terminal prices.
         *
         * @param value number of paths
         * @return this builder
         * @since 0.23.1
         */
        public Builder iterationCount(int value) {
            iterationCount = value;
            return this;
        }

        /**
         * Sets the positive historical-return lookback.
         *
         * @param value lookback in bars
         * @return this builder
         * @since 0.23.1
         */
        public Builder lookbackBarCount(int value) {
            lookbackBarCount = value;
            return this;
        }

        /**
         * Sets the deterministic base seed.
         *
         * @param value base seed
         * @return this builder
         * @since 0.23.1
         */
        public Builder seed(long value) {
            seed = value;
            return this;
        }

        /**
         * Sets the simulated shock source.
         *
         * @param value shock model
         * @return this builder
         * @since 0.23.1
         */
        public Builder shockModel(MonteCarloReturnProjectionIndicator.ShockModel value) {
            shockModel = value;
            return this;
        }

        /**
         * Sets within-path volatility behavior.
         *
         * @param value volatility update mode
         * @return this builder
         * @since 0.23.1
         */
        public Builder volatilityUpdateMode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode value) {
            volatilityUpdateMode = value;
            return this;
        }

        /**
         * Sets the EWMA decay used by within-path volatility updates.
         *
         * @param value decay factor in {@code (0, 1)}
         * @return this builder
         * @since 0.23.1
         */
        public Builder volatilityDecayFactor(double value) {
            volatilityDecayFactor = value;
            return this;
        }

        /**
         * Sets the quantile probabilities summarized from terminal prices.
         *
         * @param probabilities probabilities in {@code [0, 1]}
         * @return this builder
         * @since 0.23.1
         */
        public Builder quantiles(double... probabilities) {
            Objects.requireNonNull(probabilities, "probabilities must not be null");
            Double[] boxed = new Double[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                boxed[i] = probabilities[i];
            }
            quantileProbabilities = List.of(boxed);
            return this;
        }

        /**
         * Builds the validated exact price projection.
         *
         * @return configured price projection
         * @since 0.23.1
         */
        public MonteCarloPriceForecastIndicator build() {
            return new MonteCarloPriceForecastIndicator(this);
        }

        private MonteCarloSettings settings() {
            return new MonteCarloSettings(horizon, iterationCount, lookbackBarCount, seed, shockModel,
                    volatilityUpdateMode, volatilityDecayFactor, quantileProbabilities);
        }
    }
}

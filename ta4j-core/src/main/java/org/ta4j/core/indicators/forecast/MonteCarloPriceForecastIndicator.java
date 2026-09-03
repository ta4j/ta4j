/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.analysis.montecarlo.MonteCarloMethod;
import org.ta4j.core.analysis.montecarlo.ShockPathMonteCarloMethod;
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
    private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
    private final MonteCarloSettings settings;
    private final MonteCarloSimulation simulation;
    private final MonteCarloReturnProjectionIndicator.ShockModel shockModel;
    private final MonteCarloReturnProjectionIndicator.VolatilityUpdateMode volatilityUpdateMode;
    private final double volatilityDecayFactor;

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
        super(builder.priceIndicator, builder.stateIndicator);
        this.priceIndicator = builder.priceIndicator;
        this.stateIndicator = builder.stateIndicator;
        this.settings = builder.settings();
        this.shockModel = builder.shockModel;
        this.volatilityUpdateMode = builder.volatilityUpdateMode;
        this.volatilityDecayFactor = builder.volatilityDecayFactor;
        this.simulation = new MonteCarloSimulation(builder.stateIndicator, builder.settings(),
                builder.methodOrDefault());
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
        if (!MonteCarloSimulation.isPerPathRngSelected()) {
            return super.getValue(index);
        }
        return AccelerationRuntime.value(this, index).orElseGet(() -> super.getValue(index));
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
     * <p>
     * Online change-point states restart their estimation after a head advance, so
     * every cached forecast must be discarded and recomputed from the restarted
     * posterior.
     *
     * @since 0.24.2
     */
    @Override
    protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
        return simulation.stateRestartsAfterHeadAdvance();
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

    /**
     * Returns the read-only configuration and source indicators needed by optional
     * batch acceleration adapters.
     *
     * <p>
     * The returned specification is descriptive only. It does not authorize
     * arbitrary graph compilation; an accelerator adapter must still validate the
     * concrete graph, source series, numeric representation, and provider
     * capability before using it.
     *
     * @return acceleration specification for this forecast indicator
     * @since 0.24.2
     */
    public MonteCarloPriceForecastSpec accelerationSpec() {
        return new MonteCarloPriceForecastSpec(priceIndicator, stateIndicator, settings.horizon(),
                settings.iterationCount(), settings.lookbackBarCount(), settings.seed(), shockModel,
                volatilityUpdateMode, volatilityDecayFactor, settings.quantileProbabilities());
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
        private MonteCarloMethod monteCarloMethod;

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
         * Overrides the Monte Carlo technique with a custom implementation, replacing
         * the configured shock model, volatility update mode, and decay factor.
         *
         * @param value technique generating terminal samples
         * @return this builder
         * @since 0.24.2
         */
        public Builder monteCarloMethod(MonteCarloMethod value) {
            monteCarloMethod = Objects.requireNonNull(value, "monteCarloMethod must not be null");
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
            return new MonteCarloSettings(horizon, iterationCount, lookbackBarCount, seed, quantileProbabilities);
        }

        private MonteCarloMethod methodOrDefault() {
            return monteCarloMethod != null ? monteCarloMethod
                    : new ShockPathMonteCarloMethod(shockModel, volatilityUpdateMode, volatilityDecayFactor);
        }
    }
}

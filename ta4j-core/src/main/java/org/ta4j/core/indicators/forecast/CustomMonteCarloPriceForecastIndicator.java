/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.montecarlo.MonteCarloMethod;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.num.Num;

/**
 * Monte Carlo terminal-price forecast driven by a custom
 * {@link MonteCarloMethod}.
 *
 * <p>
 * The method generates cumulative log-returns in the series' {@code Num} type;
 * each is mapped to its terminal price with the same exponential and guards as
 * {@link MonteCarloPriceForecastIndicator} before the distribution is
 * summarized. Built through
 * {@link MonteCarloPriceForecastIndicator.Builder#monteCarloMethod(MonteCarloMethod)}.
 * A custom method always runs on the CPU.
 *
 * @since 0.26.1
 */
public final class CustomMonteCarloPriceForecastIndicator extends CachedIndicator<Forecast>
        implements ForecastProjectionIndicator {

    private final Indicator<Num> priceIndicator;
    private final MonteCarloSimulation simulation;

    private CustomMonteCarloPriceForecastIndicator(Builder builder) {
        super(builder.priceIndicator, builder.stateIndicator);
        this.priceIndicator = builder.priceIndicator;
        this.simulation = new MonteCarloSimulation(
                builder.stateIndicator, new MonteCarloSettings(builder.horizon, builder.iterationCount,
                        builder.lookbackBarCount, builder.seed, builder.quantileProbabilities),
                builder.monteCarloMethod);
    }

    @Override
    protected Forecast calculate(int index) {
        Num price = priceIndicator.getValue(index);
        if (!Num.isFinite(price) || !price.isPositive()) {
            return Forecast.unstable(index, getHorizon());
        }
        Num exponentLimit = price.getNumFactory().numOf(MonteCarloKernel.MAX_EXPONENT);
        return simulation.project(index, cumulativeReturn -> MonteCarloPriceForecastIndicator.terminalPrice(price,
                cumulativeReturn, exponentLimit));
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.26.1
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
     * @since 0.26.1
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
     * @since 0.26.1
     */
    @Override
    protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
        return simulation.stateRestartsAfterHeadAdvance();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.26.1
     */
    @Override
    public int getHorizon() {
        return simulation.getHorizon();
    }

    /**
     * Builder of a custom-method price forecast, obtained from
     * {@link MonteCarloPriceForecastIndicator.Builder#monteCarloMethod(MonteCarloMethod)}.
     *
     * @since 0.26.1
     */
    public static final class Builder {

        private final Indicator<Num> priceIndicator;
        private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
        private final MonteCarloMethod monteCarloMethod;
        private int horizon;
        private int iterationCount;
        private int lookbackBarCount;
        private long seed;
        private List<Double> quantileProbabilities;

        Builder(Indicator<Num> priceIndicator, ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator,
                MonteCarloMethod monteCarloMethod) {
            this.priceIndicator = priceIndicator;
            this.stateIndicator = stateIndicator;
            this.monteCarloMethod = Objects.requireNonNull(monteCarloMethod, "monteCarloMethod must not be null");
        }

        /**
         * Sets the positive forecast horizon in bars.
         *
         * @param value horizon in bars
         * @return this builder
         * @since 0.26.1
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
         * @since 0.26.1
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
         * @since 0.26.1
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
         * @since 0.26.1
         */
        public Builder seed(long value) {
            seed = value;
            return this;
        }

        /**
         * Sets the quantile probabilities summarized from terminal prices.
         *
         * @param probabilities probabilities in {@code [0, 1]}
         * @return this builder
         * @since 0.26.1
         */
        public Builder quantiles(double... probabilities) {
            Objects.requireNonNull(probabilities, "probabilities must not be null");
            Double[] boxed = new Double[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                boxed[i] = probabilities[i];
            }
            return quantileProbabilities(List.of(boxed));
        }

        Builder quantileProbabilities(List<Double> probabilities) {
            quantileProbabilities = probabilities;
            return this;
        }

        /**
         * Builds the validated custom-method price projection.
         *
         * @return configured price projection
         * @since 0.26.1
         */
        public CustomMonteCarloPriceForecastIndicator build() {
            return new CustomMonteCarloPriceForecastIndicator(this);
        }
    }
}

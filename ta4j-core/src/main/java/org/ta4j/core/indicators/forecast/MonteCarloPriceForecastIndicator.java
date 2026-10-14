/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.KernelIndicator;
import org.ta4j.core.analysis.montecarlo.MonteCarloMethod;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Exact Monte Carlo terminal-price forecast indicator.
 *
 * <p>
 * Each cumulative log-return path is converted to its terminal price before the
 * empirical distribution is summarized, so every returned moment and quantile
 * describes the same transformed paths. Paths are simulated in IEEE-754 double
 * precision by {@link MonteCarloKernel} whatever the series {@code Num} type;
 * terminal prices are mapped in the price's own {@code Num} type.
 *
 * <p>
 * An installed acceleration provider (for example a GPU) can compute this
 * indicator inside an {@link AccelerationRuntime} scope, with results bitwise
 * identical to the CPU. See {@link AccelerationRuntime} for how acceleration is
 * enabled. A custom {@link MonteCarloMethod} builds a
 * {@link CustomMonteCarloPriceForecastIndicator} instead.
 *
 * @since 0.22.9
 */
public final class MonteCarloPriceForecastIndicator extends KernelIndicator<Forecast>
        implements ForecastProjectionIndicator {

    private final Indicator<Num> priceIndicator;
    private final ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator;
    private final ReturnIndicator returnIndicator;
    private final MonteCarloSettings settings;
    private final int simulationUnstableBars;

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
        super(builder.kernel(), builder.priceIndicator, builder.stateIndicator);
        this.priceIndicator = builder.priceIndicator;
        this.stateIndicator = builder.stateIndicator;
        this.returnIndicator = MonteCarloSimulation.validatedReturnIndicator(builder.stateIndicator);
        this.settings = ((MonteCarloKernel) kernel()).settings();
        this.simulationUnstableBars = MonteCarloSimulation.countOfUnstableBars(stateIndicator, returnIndicator,
                settings);
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

    /**
     * Snapshots the spot price and the stable log-return moments of a decision
     * index, normalized to double precision.
     *
     * @since 0.26.1
     */
    @Override
    protected boolean snapshot(int index, double[] rowInputs) {
        if (index < simulationUnstableBars) {
            return false;
        }
        Num price = priceIndicator.getValue(index);
        if (!Num.isFinite(price) || !price.isPositive()) {
            return false;
        }
        ReturnMomentState rawState = stateIndicator.getValue(index);
        if (rawState == null) {
            return false;
        }
        ReturnMoments moments = rawState.moments();
        if (moments == null || moments.index() != index || !moments.isStable()
                || moments.representation() != ReturnRepresentation.LOG || moments.observationCount() <= 0) {
            return false;
        }
        double mean = toDouble(moments.mean());
        double drift = toDouble(moments.drift());
        double variance = toDouble(moments.variance());
        if (Double.isNaN(mean) || Double.isNaN(drift) || Double.isNaN(variance)) {
            return false;
        }
        rowInputs[MonteCarloKernel.INPUT_PRICES] = price.doubleValue();
        rowInputs[MonteCarloKernel.INPUT_MEANS] = mean;
        rowInputs[MonteCarloKernel.INPUT_DRIFTS] = drift;
        rowInputs[MonteCarloKernel.INPUT_VARIANCES] = variance;
        return true;
    }

    /**
     * Returns the historical log return of a bar, normalized to double precision.
     *
     * @since 0.26.1
     */
    @Override
    protected double windowValue(int barIndex) {
        return toDouble(returnIndicator.getValue(barIndex));
    }

    /**
     * Maps simulated cumulative log-returns to terminal prices and summarizes them;
     * any non-finite return or terminal price makes the forecast unstable.
     *
     * @since 0.26.1
     */
    @Override
    protected Forecast decode(int index, double[] outputs) {
        Num price = priceIndicator.getValue(index);
        Num exponentLimit = price.getNumFactory().numOf(MonteCarloKernel.MAX_EXPONENT);
        List<Num> samples = new ArrayList<>(outputs.length);
        for (double raw : outputs) {
            if (!Double.isFinite(raw)) {
                return unavailable(index);
            }
            Num terminal;
            try {
                terminal = terminalPrice(price, DoubleNum.valueOf(raw), exponentLimit);
            } catch (ArithmeticException exception) {
                return unavailable(index);
            }
            if (terminal == null || !Num.isFinite(terminal)) {
                return unavailable(index);
            }
            samples.add(terminal);
        }
        return Forecast.ofSamples(index, settings.horizon(), samples, settings.quantileProbabilities());
    }

    /**
     * Returns an unstable forecast.
     *
     * @since 0.26.1
     */
    @Override
    protected Forecast unavailable(int index) {
        return Forecast.unstable(index, settings.horizon());
    }

    /**
     * Maps one simulated cumulative log-return to its terminal price, shared by
     * every Monte Carlo price forecast so all apply the same exponential and
     * guards.
     *
     * @return terminal price, or {@code null} when the return exceeds the exponent
     *         limit, does not survive normalization, or the price underflows
     */
    static Num terminalPrice(Num price, Num cumulativeReturn, Num exponentLimit) {
        NumFactory numFactory = price.getNumFactory();
        // A double's decimal round trip is the identity except that -0.0 becomes
        // +0.0, which adding 0.0 reproduces without the per-sample BigDecimal.
        Num normalizedReturn = cumulativeReturn instanceof DoubleNum && numFactory instanceof DoubleNumFactory
                ? DoubleNum.valueOf(cumulativeReturn.doubleValue() + 0.0d)
                : numFactory.numOf(cumulativeReturn.bigDecimalValue());
        if (!Num.isFinite(normalizedReturn) || normalizedReturn.isZero() && !cumulativeReturn.isZero()
                || normalizedReturn.abs().isGreaterThan(exponentLimit)) {
            return null;
        }
        Num growth = normalizedReturn.exp();
        Num terminalPrice = price.multipliedBy(growth);
        return terminalPrice.isZero() && !growth.isZero() ? null : terminalPrice;
    }

    /**
     * Normalizes a series value to a double, or returns NaN when it is not finite
     * or underflows to zero. For a {@link DoubleNum} the decimal round trip is the
     * identity except that {@code -0.0} becomes {@code +0.0}, which adding
     * {@code 0.0} reproduces without the per-value decimal conversion.
     */
    private static double toDouble(Num value) {
        if (value instanceof DoubleNum) {
            double raw = value.doubleValue();
            return Double.isFinite(raw) ? raw + 0.0d : Double.NaN;
        }
        if (!Num.isFinite(value)) {
            return Double.NaN;
        }
        Num normalized = DoubleNumFactory.getInstance().numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized.doubleValue()
                : Double.NaN;
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
        return Math.max(priceIndicator.getCountOfUnstableBars(), simulationUnstableBars);
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
        return stateIndicator.restartsAfterHeadAdvance();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getHorizon() {
        return settings.horizon();
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
         * Replaces the shock-path simulation with a custom Monte Carlo technique. The
         * returned builder keeps this builder's sources, horizon, iteration count,
         * lookback, seed, and quantiles; the shock model, volatility update mode, and
         * decay factor do not apply to a custom technique.
         *
         * @param value technique generating terminal samples
         * @return builder of a {@link CustomMonteCarloPriceForecastIndicator}
         * @since 0.24.2
         */
        public CustomMonteCarloPriceForecastIndicator.Builder monteCarloMethod(MonteCarloMethod value) {
            return new CustomMonteCarloPriceForecastIndicator.Builder(priceIndicator, stateIndicator, value)
                    .horizon(horizon)
                    .iterationCount(iterationCount)
                    .lookbackBarCount(lookbackBarCount)
                    .seed(seed)
                    .quantileProbabilities(quantileProbabilities);
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

        private MonteCarloKernel kernel() {
            return new MonteCarloKernel(
                    new MonteCarloSettings(horizon, iterationCount, lookbackBarCount, seed, quantileProbabilities),
                    shockModel, volatilityUpdateMode, volatilityDecayFactor);
        }
    }
}

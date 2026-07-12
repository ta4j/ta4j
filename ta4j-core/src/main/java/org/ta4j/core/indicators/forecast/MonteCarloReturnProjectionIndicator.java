/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Monte Carlo cumulative log-return forecast indicator.
 *
 * <p>
 * Values from custom return-state implementations are normalized to the source
 * series {@link NumFactory}. A state is treated as unstable when its common
 * values are unavailable, negative where prohibited, or cannot be represented
 * by that factory.
 *
 * @since 0.22.9
 */
public final class MonteCarloReturnProjectionIndicator extends CachedIndicator<Forecast<Num>>
        implements ReturnForecastProjectionIndicator {

    private final ReturnIndicator returnIndicator;
    private final ReturnForecastStateIndicator<? extends ForecastState> stateIndicator;
    private final int horizon;
    private final int iterationCount;
    private final int lookbackBarCount;
    private final long seed;
    private final ShockModel shockModel;
    private final VolatilityUpdateMode volatilityUpdateMode;
    private final double volatilityDecayFactor;
    private final List<Double> quantileProbabilities;

    /**
     * Constructor using default Monte Carlo settings.
     *
     * @param stateIndicator log-return state indicator
     * @since 0.22.9
     */
    public MonteCarloReturnProjectionIndicator(ReturnForecastStateIndicator<? extends ForecastState> stateIndicator) {
        this(stateIndicator, 1);
    }

    /**
     * Constructor using default Monte Carlo settings for the requested horizon.
     *
     * @param stateIndicator log-return state indicator
     * @param horizon        forecast horizon in bars
     * @since 0.22.9
     */
    public MonteCarloReturnProjectionIndicator(ReturnForecastStateIndicator<? extends ForecastState> stateIndicator,
            int horizon) {
        this(builder(stateIndicator).horizon(horizon));
    }

    /**
     * Creates an indicator from the supplied builder.
     *
     * @param builder builder
     */
    private MonteCarloReturnProjectionIndicator(Builder builder) {
        super(requireSameSeries(builder.stateIndicator));
        this.stateIndicator = builder.stateIndicator;
        this.returnIndicator = builder.stateIndicator.getReturnIndicator();
        this.horizon = validatePositive("horizon", builder.horizon);
        this.iterationCount = validatePositive("iterationCount", builder.iterationCount);
        this.lookbackBarCount = validatePositive("lookbackBarCount", builder.lookbackBarCount);
        this.seed = builder.seed;
        this.shockModel = Objects.requireNonNull(builder.shockModel, "shockModel must not be null");
        this.volatilityUpdateMode = Objects.requireNonNull(builder.volatilityUpdateMode,
                "volatilityUpdateMode must not be null");
        this.volatilityDecayFactor = validateDecayFactor(builder.volatilityDecayFactor);
        this.quantileProbabilities = validateQuantiles(builder.quantileProbabilities);
    }

    /**
     * Returns a builder for this indicator.
     *
     * @param stateIndicator log-return state indicator
     * @return builder
     * @since 0.22.9
     */
    public static Builder builder(ReturnForecastStateIndicator<? extends ForecastState> stateIndicator) {
        return new Builder(stateIndicator);
    }

    @Override
    protected Forecast<Num> calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return Forecast.unstable(index, horizon);
        }
        ForecastState rawState = stateIndicator.getValue(index);
        if (rawState == null || !rawState.isStable()) {
            return Forecast.unstable(index, horizon);
        }
        NumFactory numFactory = getBarSeries().numFactory();
        ProjectionState state = ProjectionState.from(rawState, numFactory);
        if (state == null) {
            return Forecast.unstable(index, horizon);
        }
        List<Num> historicalReturns = historicalReturns(index);
        if (historicalReturns.size() < lookbackBarCount) {
            return Forecast.unstable(index, horizon);
        }

        ShockSampler sampler = ShockSampler.create(shockModel, historicalReturns, state, numFactory);
        RandomGenerator random = new SplittableRandom(mixSeed(seed, index, horizon));
        List<Num> cumulativeReturns = new ArrayList<>(iterationCount);
        for (int iteration = 0; iteration < iterationCount; iteration++) {
            cumulativeReturns.add(simulatePath(random, sampler, state, numFactory));
        }
        return Forecast.ofSamples(index, horizon, cumulativeReturns, quantileProbabilities);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(stateIndicator.getCountOfUnstableBars(),
                returnIndicator.getCountOfUnstableBars() + lookbackBarCount - 1);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public ReturnRepresentation getReturnRepresentation() {
        return ReturnRepresentation.LOG;
    }

    private List<Num> historicalReturns(int index) {
        int startIndex = index - lookbackBarCount + 1;
        List<Num> values = new ArrayList<>(lookbackBarCount);
        for (int i = startIndex; i <= index; i++) {
            Num value = returnIndicator.getValue(i);
            if (Num.isFinite(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Num simulatePath(RandomGenerator random, ShockSampler sampler, ProjectionState startingState,
            NumFactory numFactory) {
        Num cumulativeReturn = numFactory.zero();
        Num drift = startingState.drift();
        Num mean = startingState.mean();
        Num variance = startingState.variance();
        Num volatility = startingState.volatility();
        for (int step = 0; step < horizon; step++) {
            Num stepReturn = stepReturn(random, sampler, drift, volatility);
            cumulativeReturn = cumulativeReturn.plus(stepReturn);
            if (volatilityUpdateMode == VolatilityUpdateMode.EWMA) {
                Num decay = numFactory.numOf(volatilityDecayFactor);
                Num oneMinusDecay = numFactory.one().minus(decay);
                Num deviation = stepReturn.minus(mean);
                mean = mean.multipliedBy(decay).plus(stepReturn.multipliedBy(oneMinusDecay));
                variance = variance.multipliedBy(decay)
                        .plus(deviation.multipliedBy(deviation).multipliedBy(oneMinusDecay));
                volatility = variance.sqrt();
            }
        }
        return cumulativeReturn;
    }

    private Num stepReturn(RandomGenerator random, ShockSampler sampler, Num drift, Num volatility) {
        Num shock = sampler.sample(random);
        if (shockModel == ShockModel.HISTORICAL_BOOTSTRAP) {
            return shock;
        }
        return drift.plus(volatility.multipliedBy(shock));
    }

    private static long mixSeed(long seed, int index, int horizon) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L + ((long) index << 32) + index;
        value = Long.rotateLeft(value, 27) * 0x3C79AC492BA7B653L;
        value ^= 0x1C69B3F74AC4AE35L + horizon;
        value = Long.rotateLeft(value, 31) * 0x1C69B3F74AC4AE35L;
        return value ^ value >>> 33;
    }

    private static int validatePositive(String fieldName, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be >= 1");
        }
        return value;
    }

    private static double validateDecayFactor(double decayFactor) {
        if (Double.isNaN(decayFactor) || decayFactor <= 0d || decayFactor >= 1d) {
            throw new IllegalArgumentException("volatilityDecayFactor must be in (0, 1)");
        }
        return decayFactor;
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

    private static ReturnForecastStateIndicator<? extends ForecastState> validateLogStateIndicator(
            ReturnForecastStateIndicator<? extends ForecastState> stateIndicator) {
        ReturnForecastStateIndicator<? extends ForecastState> validated = Objects.requireNonNull(stateIndicator,
                "stateIndicator must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("stateIndicator must use ReturnRepresentation.LOG");
        }
        return validated;
    }

    private static ReturnForecastStateIndicator<? extends ForecastState> requireSameSeries(
            ReturnForecastStateIndicator<? extends ForecastState> stateIndicator) {
        ReturnForecastStateIndicator<? extends ForecastState> validated = validateLogStateIndicator(stateIndicator);
        IndicatorUtils.requireSameSeries(validated.getReturnIndicator(), validated);
        return validated;
    }

    /**
     * Shock source for simulated paths.
     *
     * @since 0.22.9
     */
    public enum ShockModel {

        /**
         * Sample raw historical returns.
         *
         * @since 0.22.9
         */
        HISTORICAL_BOOTSTRAP,

        /**
         * Sample standardized empirical residuals and rescale by current state.
         *
         * @since 0.22.9
         */
        STANDARDIZED_EMPIRICAL,

        /**
         * Sample standard normal shocks and rescale by current state.
         *
         * @since 0.22.9
         */
        NORMAL
    }

    /**
     * Volatility behavior inside simulated paths.
     *
     * @since 0.22.9
     */
    public enum VolatilityUpdateMode {

        /**
         * Keep volatility fixed for each path.
         *
         * @since 0.22.9
         */
        CONSTANT,

        /**
         * Update path volatility with EWMA after each simulated step.
         *
         * @since 0.22.9
         */
        EWMA
    }

    /**
     * Builder for {@link MonteCarloReturnProjectionIndicator}.
     *
     * @since 0.22.9
     */
    public static final class Builder {

        private final ReturnForecastStateIndicator<? extends ForecastState> stateIndicator;
        private int horizon = 1;
        private int iterationCount = 1_000;
        private int lookbackBarCount = 252;
        private long seed = 42L;
        private ShockModel shockModel = ShockModel.STANDARDIZED_EMPIRICAL;
        private VolatilityUpdateMode volatilityUpdateMode = VolatilityUpdateMode.CONSTANT;
        private double volatilityDecayFactor = 0.94d;
        private List<Double> quantileProbabilities = Forecast.DEFAULT_QUANTILE_PROBABILITIES;

        private Builder(ReturnForecastStateIndicator<? extends ForecastState> stateIndicator) {
            this.stateIndicator = Objects.requireNonNull(stateIndicator, "stateIndicator must not be null");
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
         * Sets the quantiles to include in returned forecasts.
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
         * Builds the indicator.
         *
         * @return indicator
         * @since 0.22.9
         */
        public MonteCarloReturnProjectionIndicator build() {
            return new MonteCarloReturnProjectionIndicator(this);
        }
    }

    private record ProjectionState(Num mean, Num drift, Num variance, Num volatility) {

        private static ProjectionState from(ForecastState state, NumFactory numFactory) {
            Num mean = normalize(state.mean(), numFactory);
            Num drift = normalize(state.drift(), numFactory);
            Num variance = normalize(state.variance(), numFactory);
            Num volatility = normalize(state.volatility(), numFactory);
            if (!Num.isFinite(mean) || !Num.isFinite(drift) || !Num.isFinite(variance) || !Num.isFinite(volatility)
                    || variance.isNegative() || volatility.isNegative()) {
                return null;
            }
            return new ProjectionState(mean, drift, variance, volatility);
        }

        private static Num normalize(Num value, NumFactory numFactory) {
            if (!Num.isFinite(value)) {
                return NaN.NaN;
            }
            Num normalized = numFactory.produces(value) ? value : numFactory.numOf(value.toString());
            return Num.isFinite(normalized) ? normalized : NaN.NaN;
        }
    }

    @FunctionalInterface
    private interface ShockSampler {

        Num sample(RandomGenerator random);

        static ShockSampler create(ShockModel model, List<Num> historicalReturns, ProjectionState state,
                NumFactory numFactory) {
            return switch (model) {
            case HISTORICAL_BOOTSTRAP -> historicalBootstrap(historicalReturns);
            case STANDARDIZED_EMPIRICAL -> standardizedEmpirical(historicalReturns, state, numFactory);
            case NORMAL -> random -> numFactory.numOf(random.nextGaussian());
            };
        }

        private static ShockSampler historicalBootstrap(List<Num> historicalReturns) {
            List<Num> samples = List.copyOf(historicalReturns);
            return random -> samples.get(random.nextInt(samples.size()));
        }

        private static ShockSampler standardizedEmpirical(List<Num> historicalReturns, ProjectionState state,
                NumFactory numFactory) {
            List<Num> shocks = new ArrayList<>(historicalReturns.size());
            if (state.volatility().isZero()) {
                shocks.add(numFactory.zero());
            } else {
                for (Num historicalReturn : historicalReturns) {
                    shocks.add(historicalReturn.minus(state.mean()).dividedBy(state.volatility()));
                }
            }
            List<Num> samples = List.copyOf(shocks);
            return random -> samples.get(random.nextInt(samples.size()));
        }
    }
}

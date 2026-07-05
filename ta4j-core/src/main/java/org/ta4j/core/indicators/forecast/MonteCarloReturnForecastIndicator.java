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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.walkforward.PredictionSnapshot;

/**
 * Monte Carlo cumulative log-return forecast indicator.
 *
 * @since 0.22.9
 */
public final class MonteCarloReturnForecastIndicator extends CachedIndicator<PredictionSnapshot.Forecast<Num>>
        implements ForecastPredictionIndicator {

    private static final int DEFAULT_STATE_INITIALIZATION_BAR_COUNT = 30;
    private static final double DEFAULT_STATE_DECAY_FACTOR = 0.94d;

    private final Indicator<Num> returnIndicator;
    private final Indicator<ReturnForecastState> stateIndicator;
    private final int horizon;
    private final int iterationCount;
    private final int lookbackBarCount;
    private final long seed;
    private final ShockModel shockModel;
    private final VolatilityUpdateMode volatilityUpdateMode;
    private final double volatilityDecayFactor;
    private final List<Double> quantileProbabilities;

    /**
     * Constructor using default EWMA state and default Monte Carlo settings.
     *
     * @param returnIndicator log-return source
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator) {
        this(returnIndicator, 1);
    }

    /**
     * Constructor using default EWMA state and default Monte Carlo settings for the
     * requested horizon.
     *
     * @param returnIndicator log-return source
     * @param horizon         forecast horizon in bars
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator, int horizon) {
        this(returnIndicator, horizon, DEFAULT_STATE_INITIALIZATION_BAR_COUNT, DEFAULT_STATE_DECAY_FACTOR);
    }

    /**
     * Constructor using zero-drift EWMA state and default Monte Carlo settings for
     * the requested horizon.
     *
     * @param returnIndicator        log-return source
     * @param horizon                forecast horizon in bars
     * @param initializationBarCount observations required before state is stable
     * @param decayFactor            EWMA decay factor in {@code (0, 1)}
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator, int horizon, int initializationBarCount,
            double decayFactor) {
        this(returnIndicator, horizon, initializationBarCount, decayFactor, ForecastStateIndicator.DriftMode.ZERO);
    }

    /**
     * Constructor using EWMA state and default Monte Carlo settings for the
     * requested horizon.
     *
     * @param returnIndicator        log-return source
     * @param horizon                forecast horizon in bars
     * @param initializationBarCount observations required before state is stable
     * @param decayFactor            EWMA decay factor in {@code (0, 1)}
     * @param driftMode              drift assumption
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator, int horizon, int initializationBarCount,
            double decayFactor, ForecastStateIndicator.DriftMode driftMode) {
        this(returnIndicator,
                new ForecastStateIndicator(returnIndicator, initializationBarCount, decayFactor, driftMode), horizon);
    }

    /**
     * Constructor using default Monte Carlo settings.
     *
     * @param returnIndicator log-return source
     * @param stateIndicator  return state source
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator,
            Indicator<ReturnForecastState> stateIndicator) {
        this(returnIndicator, stateIndicator, 1);
    }

    /**
     * Constructor using default Monte Carlo settings for the requested horizon.
     *
     * @param returnIndicator log-return source
     * @param stateIndicator  return state source
     * @param horizon         forecast horizon in bars
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator,
            Indicator<ReturnForecastState> stateIndicator, int horizon) {
        this(builder(returnIndicator, stateIndicator).horizon(horizon));
    }

    private MonteCarloReturnForecastIndicator(Builder builder) {
        super(IndicatorUtils.requireSameSeries(
                Objects.requireNonNull(builder.returnIndicator, "returnIndicator must not be null"),
                Objects.requireNonNull(builder.stateIndicator, "stateIndicator must not be null")));
        this.returnIndicator = builder.returnIndicator;
        this.stateIndicator = builder.stateIndicator;
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
     * @param returnIndicator log-return source
     * @param stateIndicator  return state source
     * @return builder
     * @since 0.22.9
     */
    public static Builder builder(Indicator<Num> returnIndicator, Indicator<ReturnForecastState> stateIndicator) {
        return new Builder(returnIndicator, stateIndicator);
    }

    @Override
    protected PredictionSnapshot.Forecast<Num> calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return PredictionSnapshot.Forecast.unstable(index, horizon);
        }
        ReturnForecastState state = stateIndicator.getValue(index);
        if (state == null || !state.isStable() || IndicatorUtils.isInvalid(state.volatility())
                || IndicatorUtils.isInvalid(state.drift())) {
            return PredictionSnapshot.Forecast.unstable(index, horizon);
        }
        List<Num> historicalReturns = historicalReturns(index);
        if (historicalReturns.size() < lookbackBarCount) {
            return PredictionSnapshot.Forecast.unstable(index, horizon);
        }

        NumFactory numFactory = getBarSeries().numFactory();
        ShockSampler sampler = ShockSampler.create(shockModel, historicalReturns, state, numFactory);
        RandomGenerator random = new SplittableRandom(mixSeed(seed, index, horizon));
        List<Num> cumulativeReturns = new ArrayList<>(iterationCount);
        for (int iteration = 0; iteration < iterationCount; iteration++) {
            cumulativeReturns.add(simulatePath(random, sampler, state, numFactory));
        }
        return PredictionSnapshot.Forecast.ofSamples(index, horizon, cumulativeReturns, quantileProbabilities);
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

    private List<Num> historicalReturns(int index) {
        int startIndex = index - lookbackBarCount + 1;
        List<Num> values = new ArrayList<>(lookbackBarCount);
        for (int i = startIndex; i <= index; i++) {
            Num value = returnIndicator.getValue(i);
            if (!IndicatorUtils.isInvalid(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Num simulatePath(RandomGenerator random, ShockSampler sampler, ReturnForecastState startingState,
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
     * Builder for {@link MonteCarloReturnForecastIndicator}.
     *
     * @since 0.22.9
     */
    public static final class Builder {

        private final Indicator<Num> returnIndicator;
        private final Indicator<ReturnForecastState> stateIndicator;
        private int horizon = 1;
        private int iterationCount = 1_000;
        private int lookbackBarCount = 252;
        private long seed = 42L;
        private ShockModel shockModel = ShockModel.STANDARDIZED_EMPIRICAL;
        private VolatilityUpdateMode volatilityUpdateMode = VolatilityUpdateMode.CONSTANT;
        private double volatilityDecayFactor = 0.94d;
        private List<Double> quantileProbabilities = PredictionSnapshot.Forecast.DEFAULT_QUANTILE_PROBABILITIES;

        private Builder(Indicator<Num> returnIndicator, Indicator<ReturnForecastState> stateIndicator) {
            this.returnIndicator = Objects.requireNonNull(returnIndicator, "returnIndicator must not be null");
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
        public MonteCarloReturnForecastIndicator build() {
            return new MonteCarloReturnForecastIndicator(this);
        }
    }

    @FunctionalInterface
    private interface ShockSampler {

        Num sample(RandomGenerator random);

        static ShockSampler create(ShockModel model, List<Num> historicalReturns, ReturnForecastState state,
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

        private static ShockSampler standardizedEmpirical(List<Num> historicalReturns, ReturnForecastState state,
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

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Monte Carlo cumulative log-return forecast indicator.
 *
 * @since 0.22.9
 */
public class MonteCarloReturnForecastIndicator extends CachedIndicator<ForecastDistribution<Num>>
        implements ForecastDistributionIndicator {

    private final Indicator<Num> returnIndicator;
    private final Indicator<ReturnForecastState> stateIndicator;
    private final MonteCarloForecastConfig config;

    /**
     * Constructor.
     *
     * @param returnIndicator log-return source
     * @param stateIndicator  return state source
     * @param config          Monte Carlo configuration
     * @since 0.22.9
     */
    public MonteCarloReturnForecastIndicator(Indicator<Num> returnIndicator,
            Indicator<ReturnForecastState> stateIndicator, MonteCarloForecastConfig config) {
        super(IndicatorUtils.requireSameSeries(
                Objects.requireNonNull(returnIndicator, "returnIndicator must not be null"),
                Objects.requireNonNull(stateIndicator, "stateIndicator must not be null")));
        this.returnIndicator = returnIndicator;
        this.stateIndicator = stateIndicator;
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    protected ForecastDistribution<Num> calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return ForecastDistribution.unstable(index, config.horizon());
        }
        ReturnForecastState state = stateIndicator.getValue(index);
        if (state == null || !state.isStable() || ForecastNumerics.isInvalid(state.volatility())
                || ForecastNumerics.isInvalid(state.drift())) {
            return ForecastDistribution.unstable(index, config.horizon());
        }
        List<Num> historicalReturns = historicalReturns(index);
        if (historicalReturns.size() < config.lookbackBarCount()) {
            return ForecastDistribution.unstable(index, config.horizon());
        }

        NumFactory numFactory = getBarSeries().numFactory();
        ShockSampler sampler = ShockSampler.create(config.shockModel(), historicalReturns, state, numFactory);
        RandomGenerator random = new SplittableRandom(mixSeed(config.seed(), index, config.horizon()));
        List<Num> cumulativeReturns = new ArrayList<>(config.iterationCount());
        for (int iteration = 0; iteration < config.iterationCount(); iteration++) {
            cumulativeReturns.add(simulatePath(random, sampler, state, numFactory));
        }
        return ForecastDistribution.ofSamples(index, config.horizon(), cumulativeReturns,
                config.quantileProbabilities());
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(stateIndicator.getCountOfUnstableBars(),
                returnIndicator.getCountOfUnstableBars() + config.lookbackBarCount() - 1);
    }

    private List<Num> historicalReturns(int index) {
        int startIndex = index - config.lookbackBarCount() + 1;
        List<Num> values = new ArrayList<>(config.lookbackBarCount());
        for (int i = startIndex; i <= index; i++) {
            Num value = returnIndicator.getValue(i);
            if (!ForecastNumerics.isInvalid(value)) {
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
        for (int step = 0; step < config.horizon(); step++) {
            Num stepReturn = stepReturn(random, sampler, drift, volatility);
            cumulativeReturn = cumulativeReturn.plus(stepReturn);
            if (config.volatilityUpdateMode() == VolatilityUpdateMode.EWMA) {
                Num decay = numFactory.numOf(config.volatilityDecayFactor());
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
        if (config.shockModel() == ShockModel.HISTORICAL_BOOTSTRAP) {
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

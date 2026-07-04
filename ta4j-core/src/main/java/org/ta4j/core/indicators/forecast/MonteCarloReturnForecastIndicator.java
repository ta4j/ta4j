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
        implements ForecastDistributionIndicator<Num> {

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
            return ForecastDistribution.undefined(index, config.horizon());
        }
        ReturnForecastState state = stateIndicator.getValue(index);
        if (state == null || !state.defined() || ForecastNumerics.isInvalid(state.volatility())
                || ForecastNumerics.isInvalid(state.drift())) {
            return ForecastDistribution.undefined(index, config.horizon());
        }
        List<Num> historicalReturns = historicalReturns(index);
        if (historicalReturns.size() < config.lookbackBarCount()) {
            return ForecastDistribution.undefined(index, config.horizon());
        }

        NumFactory numFactory = getBarSeries().numFactory();
        ShockSampler sampler = ShockSampler.create(config.shockModel(), historicalReturns, state, numFactory);
        RandomGenerator random = new SplittableRandom(SeedMixer.mix(config.seed(), index, config.horizon()));
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
            Num stepReturn = stepReturn(random, sampler, numFactory, drift, volatility);
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

    private Num stepReturn(RandomGenerator random, ShockSampler sampler, NumFactory numFactory, Num drift,
            Num volatility) {
        Num shock = sampler.sample(random, numFactory);
        if (config.shockModel() == ShockModel.HISTORICAL_BOOTSTRAP) {
            return shock;
        }
        return drift.plus(volatility.multipliedBy(shock));
    }
}

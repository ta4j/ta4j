/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@FunctionalInterface
interface ShockSampler {

    Num sample(RandomGenerator random, NumFactory numFactory);

    static ShockSampler create(ShockModel model, List<Num> historicalReturns, ReturnForecastState state,
            NumFactory numFactory) {
        return switch (model) {
        case HISTORICAL_BOOTSTRAP -> historicalBootstrap(historicalReturns);
        case STANDARDIZED_EMPIRICAL -> standardizedEmpirical(historicalReturns, state, numFactory);
        case NORMAL -> (random, factory) -> factory.numOf(random.nextGaussian());
        };
    }

    private static ShockSampler historicalBootstrap(List<Num> historicalReturns) {
        List<Num> samples = List.copyOf(historicalReturns);
        return (random, numFactory) -> samples.get(random.nextInt(samples.size()));
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
        return (random, factory) -> samples.get(random.nextInt(samples.size()));
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;

import org.ta4j.core.num.Num;

/**
 * 50/50 mixture of two techniques behind
 * {@link MonteCarloMethod#pooledWith(MonteCarloMethod)}.
 *
 * <p>
 * The first technique draws {@code iterationCount / 2} samples and the second
 * the remainder, each under its own {@link SplittableRandom} seeded from two
 * consecutive {@code nextLong()} draws of {@link MonteCarloContext#random()}.
 * When the parent context carries per-path streams, the first technique's paths
 * use parent paths {@code [0, iterationCount / 2)} and the second's the rest.
 * The pooled result is unstable when the budget is below 2 or either component
 * breaks the seam contract.
 */
final class EnsembleMonteCarloMethod implements MonteCarloMethod {

    private final MonteCarloMethod first;
    private final MonteCarloMethod second;

    EnsembleMonteCarloMethod(MonteCarloMethod first, MonteCarloMethod second) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = Objects.requireNonNull(second, "other");
    }

    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        if (context.iterationCount() < 2) {
            return null;
        }
        int half = context.iterationCount() / 2;
        MonteCarloContext firstContext = subContext(context, half, 0);
        MonteCarloContext secondContext = subContext(context, context.iterationCount() - half, half);
        List<Num> firstSamples = MonteCarloArithmetic.normalizeSamples(first.terminalReturns(firstContext),
                firstContext);
        if (firstSamples == null) {
            return null;
        }
        List<Num> secondSamples = MonteCarloArithmetic.normalizeSamples(second.terminalReturns(secondContext),
                secondContext);
        if (secondSamples == null) {
            return null;
        }
        List<Num> pooled = new ArrayList<>(context.iterationCount());
        pooled.addAll(firstSamples);
        pooled.addAll(secondSamples);
        return pooled;
    }

    /**
     * Each component keeps its own sequential stream; when the parent carries
     * per-path streams, the component's paths map onto the parent's paths starting
     * at {@code firstPath}, so pooled samples stay path-order independent.
     */
    private static MonteCarloContext subContext(MonteCarloContext context, int count, int firstPath) {
        IntFunction<RandomGenerator> perPathRandoms = context.perPathRandoms() == null ? null
                : path -> context.randomForPath(firstPath + path);
        return new MonteCarloContext(context.index(), context.horizon(), count, context.historicalLogReturns(),
                context.moments(), new SplittableRandom(context.random().nextLong()), context.numFactory(),
                perPathRandoms);
    }

    @Override
    public String toString() {
        return "EnsembleMonteCarloMethod[first=" + first + ", second=" + second + "]";
    }
}

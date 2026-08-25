/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.ta4j.core.num.Num;

/**
 * Composition decorator that pools two independent techniques into a single
 * sample distribution.
 *
 * <p>
 * The iteration budget is split roughly 50/50 between the two inner techniques
 * (the first receives {@code iterationCount / 2} draws, the second the
 * remainder), and the pooled samples are concatenated to form the terminal
 * distribution. Each component runs under its own {@link SplittableRandom}
 * derived from {@link MonteCarloContext#random()} via two {@code nextLong}
 * draws, so the ensemble remains deterministic under the seam's single-source
 * rule and each component reproduces its standalone draws at the reduced count.
 *
 * <p>
 * This decorator stresses the seam contract: it draws bookkeeping randomness
 * exclusively from {@link MonteCarloContext#random()}, returns exactly
 * {@code context.iterationCount()} finite samples, propagates a {@code null}
 * (unstable) result when either inner method fails, and declares the forecast
 * unstable when either component returns the wrong sample count.
 *
 * @see MonteCarloMethod
 * @since 0.24.2
 */
public final class EnsembleMonteCarloMethod implements MonteCarloMethod {

    private final MonteCarloMethod first;
    private final MonteCarloMethod second;

    /**
     * Pools two techniques 50/50.
     *
     * @param first  first technique, receives the leading half of the budget
     * @param second second technique, receives the remainder
     * @since 0.24.2
     */
    public EnsembleMonteCarloMethod(MonteCarloMethod first, MonteCarloMethod second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("first and second must not be null");
        }
        this.first = first;
        this.second = second;
    }

    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        if (context.iterationCount() < 2) {
            return null;
        }
        int half = context.iterationCount() / 2;
        int remainder = context.iterationCount() - half;
        RandomGenerator baseRandom = context.random();
        MonteCarloContext firstContext = subContext(context, half, new SplittableRandom(baseRandom.nextLong()));
        MonteCarloContext secondContext = subContext(context, remainder, new SplittableRandom(baseRandom.nextLong()));
        List<Num> firstSamples = first.terminalReturns(firstContext);
        List<Num> secondSamples = second.terminalReturns(secondContext);
        if (firstSamples == null || secondSamples == null) {
            return null;
        }
        if (firstSamples.size() != half || secondSamples.size() != remainder) {
            return null;
        }
        List<Num> pooled = new ArrayList<>(context.iterationCount());
        pooled.addAll(firstSamples);
        pooled.addAll(secondSamples);
        return pooled;
    }

    private static MonteCarloContext subContext(MonteCarloContext context, int count, RandomGenerator random) {
        return new MonteCarloContext(context.index(), context.horizon(), count, context.historicalLogReturns(),
                context.moments(), random, context.numFactory());
    }

    @Override
    public String toString() {
        return "EnsembleMonteCarloMethod[first=" + first + ", second=" + second + "]";
    }
}
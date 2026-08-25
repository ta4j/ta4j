/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.Test;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the ensemble decorator: the pooled distribution halves the budget
 * between two techniques, derives sub-generators from the context source, and
 * honors the seam null/wrong-count contract.
 */
public class EnsembleMonteCarloMethodTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    @Test
    public void poolsHalfAndRemainderInOrder() {
        MonteCarloMethod first = fixedSamplesOfSize(2, 1.0d);
        MonteCarloMethod second = fixedSamplesOfSize(3, 2.0d);
        MonteCarloMethod method = new EnsembleMonteCarloMethod(first, second);

        List<Num> samples = method.terminalReturns(context(4, 5, window(0.01d, -0.01d, 0.01d), moments(0d), 7L));

        // IterationCount 5: first gets 2, second gets 3.
        assertEquals(5, samples.size());
        assertEquals(FACTORY.numOf(1.0d), samples.get(0));
        assertEquals(FACTORY.numOf(1.0d), samples.get(1));
        assertEquals(FACTORY.numOf(2.0d), samples.get(2));
        assertEquals(FACTORY.numOf(2.0d), samples.get(3));
        assertEquals(FACTORY.numOf(2.0d), samples.get(4));
    }

    @Test
    public void subContextsGetHalfAndRemainderCounts() {
        int[] firstCount = { -1 };
        int[] secondCount = { -1 };
        MonteCarloMethod first = context -> {
            firstCount[0] = context.iterationCount();
            return null;
        };
        MonteCarloMethod second = context -> {
            secondCount[0] = context.iterationCount();
            return null;
        };
        MonteCarloMethod method = new EnsembleMonteCarloMethod(first, second);

        assertNull(method.terminalReturns(context(4, 5, window(0.01d, -0.01d, 0.01d), moments(0d), 7L)));
        assertEquals(2, firstCount[0]);
        assertEquals(3, secondCount[0]);
    }

    @Test
    public void sameSeedReproducesIdenticalSamples() {
        MonteCarloMethod method = new EnsembleMonteCarloMethod(
                new ShockPathMonteCarloMethod(ShockModel.HISTORICAL_BOOTSTRAP, VolatilityUpdateMode.EWMA, 0.94d),
                NormalInverseGammaForecastMethod.withEmpiricalPriors());
        List<Num> firstRun = method.terminalReturns(context(4, 200, window(0.01d, -0.01d, 0.01d), moments(0d), 42L));
        List<Num> secondRun = method.terminalReturns(context(4, 200, window(0.01d, -0.01d, 0.01d), moments(0d), 42L));

        assertEquals(firstRun.size(), secondRun.size());
        for (int i = 0; i < firstRun.size(); i++) {
            assertEquals(firstRun.get(i), secondRun.get(i));
        }
    }

    @Test
    public void nullComponentPropagatesAsUnstable() {
        MonteCarloMethod method = new EnsembleMonteCarloMethod(context -> null, fixedSamplesOfSize(2, 1.0d));

        assertNull(method.terminalReturns(context(4, 5, window(0.01d, -0.01d, 0.01d), moments(0d), 7L)));
    }

    @Test
    public void wrongCountComponentPropagatesAsUnstable() {
        MonteCarloMethod method = new EnsembleMonteCarloMethod(fixedSamplesOfSize(1, 1.0d),
                fixedSamplesOfSize(1, 2.0d));

        // IterationCount 4 -> half 2, remainder 2; first returns 1 (wrong count).
        assertNull(method.terminalReturns(context(4, 4, window(0.01d, -0.01d, 0.01d), moments(0d), 7L)));
    }

    @Test
    public void iterationCountBelowTwoReturnsUnstable() {
        MonteCarloMethod method = new EnsembleMonteCarloMethod(fixedSamplesOfSize(2, 1.0d),
                fixedSamplesOfSize(2, 2.0d));

        // A single draw cannot be split 50/50; the decorator refuses instead of
        // building a count-0 sub-context (MonteCarloContext requires >= 1).
        assertNull(method.terminalReturns(context(4, 1, window(0.01d, -0.01d, 0.01d), moments(0d), 7L)));
    }

    private static MonteCarloMethod fixedSamplesOfSize(int size, double value) {
        return context -> {
            List<Num> samples = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                samples.add(FACTORY.numOf(value));
            }
            return samples;
        };
    }

    private static List<Num> window(double... values) {
        List<Num> result = new ArrayList<>(values.length);
        for (double value : values) {
            result.add(FACTORY.numOf(value));
        }
        return result;
    }

    private static ReturnMoments moments(double drift) {
        return ReturnMoments.stable(100, 3, ReturnRepresentation.LOG, FACTORY.zero(), FACTORY.numOf(drift),
                FACTORY.one());
    }

    private static MonteCarloContext context(int horizon, int iterationCount, List<Num> historicalLogReturns,
            ReturnMoments moments, long seed) {
        return new MonteCarloContext(100, horizon, iterationCount, historicalLogReturns, moments,
                new SplittableRandom(seed), FACTORY);
    }
}
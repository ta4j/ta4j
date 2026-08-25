/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.Test;
import org.ta4j.core.TestUtils;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies {@link PosteriorSmoothedResidualMonteCarloMethod} against the seam's
 * contract: single-source determinism from {@code context.random()}, exact
 * iteration-count and finite-sample guarantees, null propagation, and count
 * propagation through the wrapper.
 */
public class PosteriorSmoothedResidualMonteCarloMethodTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    private static final double[] WINDOW_FINITE = { 0.012, -0.008, 0.02, -0.015, 0.005, 0.03, -0.022, 0.011, -0.004,
            0.017, -0.03, 0.009, 0.002, -0.012, 0.024, -0.007 };

    /** Inner technique returning a fixed sample per iteration. */
    private static MonteCarloMethod fixedInner(Num value) {
        return context -> {
            List<Num> samples = new ArrayList<>(context.iterationCount());
            for (int i = 0; i < context.iterationCount(); i++) {
                samples.add(value);
            }
            return samples;
        };
    }

    private static MonteCarloMethod nullInner() {
        return context -> null;
    }

    private static MonteCarloMethod wrongCountInner() {
        return context -> List.of(FACTORY.zero());
    }

    @Test
    public void sameSeedReproducesIdenticalSamples() {
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                fixedInner(FACTORY.numOf(0.5d)));
        List<Num> first = method.terminalReturns(context(4, 200, window(WINDOW_FINITE), 7L));
        List<Num> second = method.terminalReturns(context(4, 200, window(WINDOW_FINITE), 7L));

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first, second);
    }

    @Test
    public void differentSeedProducesDifferentSamples() {
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                fixedInner(FACTORY.one()));
        List<Num> first = method.terminalReturns(context(4, 100, window(WINDOW_FINITE), 7L));
        List<Num> second = method.terminalReturns(context(4, 100, window(WINDOW_FINITE), 8L));

        assertNotNull(first);
        assertNotNull(second);
        boolean differs = !first.equals(second);
        assertTrue("different seeds must produce different samples", differs);
    }

    @Test
    public void constantWindowCollapsesToPosteriorMeanDriftAccumulation() {
        // Constant window drives posterior variance (and hence the parameter draw's
        // sigma) to zero, so every path is the posterior mean drift over the
        // horizon, independent of the inner residual path.
        List<Num> constantWindow = List.of(FACTORY.numOf(0.01d), FACTORY.numOf(0.01d), FACTORY.numOf(0.01d));
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                fixedInner(FACTORY.one()));
        List<Num> samples = method.terminalReturns(context(3, 10, constantWindow, 7L));

        assertNotNull(samples);
        assertEquals(10, samples.size());
        for (Num sample : samples) {
            TestUtils.assertNumEquals(FACTORY.numOf(3 * 0.01d), sample, 1e-12);
        }
    }

    @Test
    public void posteriorScaleWidensDistributionOverInnerShape() {
        // A non-degenerate window with positive variance should make the terminal
        // distribution wider than a zero-variance panel, detected by sample
        // variance being strictly positive across draws.
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                fixedInner(FACTORY.zero()));
        List<Num> samples = method.terminalReturns(context(4, 4000, window(WINDOW_FINITE), 42L));

        assertNotNull(samples);
        assertEquals(4000, samples.size());
        Num mean = FACTORY.zero();
        for (Num sample : samples) {
            mean = mean.plus(sample);
        }
        mean = mean.dividedBy(FACTORY.numOf(samples.size()));
        Num variance = FACTORY.zero();
        for (Num sample : samples) {
            Num deviation = sample.minus(mean);
            variance = variance.plus(deviation.multipliedBy(deviation));
        }
        variance = variance.dividedBy(FACTORY.numOf(samples.size()));
        assertTrue("posterior scale must produce positive sample variance", variance.doubleValue() > 0d);
    }

    @Test
    public void emptyWindowYieldsNoSamples() {
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                fixedInner(FACTORY.one()));
        assertNull(method.terminalReturns(context(1, 5, List.of(), 1L)));
    }

    @Test
    public void innerNullPropagatesAsUnstable() {
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(nullInner());
        assertNull(method.terminalReturns(context(4, 10, window(WINDOW_FINITE), 7L)));
    }

    @Test
    public void innerWrongCountPropagatesAsUnstable() {
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                wrongCountInner());
        assertNull(method.terminalReturns(context(4, 10, window(WINDOW_FINITE), 7L)));
    }

    @Test
    public void guaranteesExactCountAndFiniteSamples() {
        PosteriorSmoothedResidualMonteCarloMethod method = new PosteriorSmoothedResidualMonteCarloMethod(
                fixedInner(FACTORY.one()));
        int count = 987;
        List<Num> samples = method.terminalReturns(context(4, count, window(WINDOW_FINITE), 99L));

        assertNotNull(samples);
        assertEquals(count, samples.size());
        for (Num sample : samples) {
            assertTrue("sample must be finite", Num.isFinite(sample));
        }
    }

    private static List<Num> window(double[] values) {
        List<Num> output = new ArrayList<>(values.length);
        for (double value : values) {
            output.add(FACTORY.numOf(value));
        }
        return output;
    }

    private static MonteCarloContext context(int horizon, int iterations, List<Num> historicalLogReturns, long seed) {
        ReturnMoments moments = ReturnMoments.stable(100, Math.max(1, historicalLogReturns.size()),
                ReturnRepresentation.LOG, FACTORY.zero(), FACTORY.zero(), FACTORY.one());
        return new MonteCarloContext(100, horizon, iterations, historicalLogReturns, moments,
                new SplittableRandom(seed), FACTORY);
    }
}
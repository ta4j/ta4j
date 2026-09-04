/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.Test;
import org.ta4j.core.TestUtils;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the Student-t scale mixing decorator: centered samples are rescaled
 * around the drift path with mean-1 scale draws, very high degrees of freedom
 * stay finite, cross-factory inner samples are coerced without throwing, and
 * the seam null/wrong-count contract is honored.
 */
public class StudentTScaleMixingMonteCarloMethodTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    @Test
    public void scaledSamplesRemainCenteredOnDriftPath() {
        // Zero-drift; the mean-1 scale preserves the sample mean.
        MonteCarloMethod inner = fixedSamples(-1.0d, 0.5d, 1.5d);
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(inner, 5);

        List<Num> samples = method.terminalReturns(context(4, 3, window(0.01d, -0.01d, 0.01d), moments(0d), 7L));

        assertEquals(3, samples.size());
        double mean = 0d;
        for (Num sample : samples) {
            mean += sample.doubleValue();
        }
        mean /= samples.size();
        TestUtils.assertNumEquals(FACTORY.numOf(0d), FACTORY.numOf(mean), 0.6d);
    }

    @Test
    public void highDegreesOfFreedomApproximateUnchangedInner() {
        MonteCarloMethod inner = fixedSamples(0.5d);
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(inner, 60);

        List<Num> samples = method.terminalReturns(context(4, 1, window(0.01d, -0.01d, 0.01d), moments(0d), 7L));

        assertEquals(1, samples.size());
        // The scale draw is concentrated near 1; allow a small tolerance.
        TestUtils.assertNumEquals(FACTORY.numOf(0.5d), samples.get(0), 0.5d);
    }

    @Test
    public void veryHighDegreesOfFreedomStayFinite() {
        // df = 300 previously overflowed the Lanczos gamma -> NaN scale mean and
        // thereby NaN samples (instability). The log-space computation keeps the
        // mean finite, so all samples must be finite.
        MonteCarloMethod inner = fixedSamples(0.5d);
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(inner, 300);

        List<Num> samples = method.terminalReturns(context(4, 1, window(0.01d, -0.01d, 0.01d), moments(0d), 7L));

        assertEquals(1, samples.size());
        for (Num sample : samples) {
            assertTrue(Double.isFinite(sample.doubleValue()));
        }
    }

    @Test
    public void foreignFactoryInnerSamplesAreCoercedWithoutThrowing() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        // Inner returns DoubleNum samples while context and moments use DecimalNum;
        // the decorator must coerce through the context factory instead of
        // throwing a ClassCastException.
        MonteCarloMethod inner = context -> {
            List<Num> samples = new ArrayList<>(1);
            samples.add(FACTORY.numOf(0.5d));
            return samples;
        };
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(inner, 5);
        ReturnMoments decimalMoments = ReturnMoments.stable(100, 3, ReturnRepresentation.LOG, decimalFactory.zero(),
                decimalFactory.zero(), decimalFactory.one());
        List<Num> samples = method.terminalReturns(new MonteCarloContext(100, 4, 1, window(0.01d, -0.01d, 0.01d),
                decimalMoments, new SplittableRandom(7L), decimalFactory));

        assertEquals(1, samples.size());
        assertTrue(Double.isFinite(samples.get(0).doubleValue()));
    }

    @Test
    public void sameSeedReproducesIdenticalSamples() {
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(
                NormalInverseGammaForecastMethod.withEmpiricalPriors());
        List<Num> firstRun = method.terminalReturns(context(3, 200, window(0.01d, -0.01d, 0.01d), moments(0d), 42L));
        List<Num> secondRun = method.terminalReturns(context(3, 200, window(0.01d, -0.01d, 0.01d), moments(0d), 42L));

        assertEquals(firstRun.size(), secondRun.size());
        for (int i = 0; i < firstRun.size(); i++) {
            assertEquals(firstRun.get(i), secondRun.get(i));
        }
    }

    @Test
    public void nullInnerPropagatesAsUnstable() {
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(context -> null);

        assertNull(method.terminalReturns(context(4, 5, window(0.01d, -0.01d, 0.01d), moments(0d), 1L)));
    }

    @Test
    public void wrongCountInnerPropagatesAsUnstable() {
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(fixedSamples(0.5d));

        assertNull(method.terminalReturns(context(4, 3, window(0.01d, -0.01d, 0.01d), moments(0d), 1L)));
    }

    @Test
    public void unstableMomentsPropagateAsUnstable() {
        MonteCarloMethod method = new StudentTScaleMixingMonteCarloMethod(fixedSamples(0.5d));
        ReturnMoments unstable = ReturnMoments.unstable(100, 2, ReturnRepresentation.LOG);

        assertNull(method.terminalReturns(context(4, 1, window(0.01d, -0.01d, 0.01d), unstable, 1L)));
    }

    @Test
    public void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new StudentTScaleMixingMonteCarloMethod(null));
        assertThrows(IllegalArgumentException.class,
                () -> new StudentTScaleMixingMonteCarloMethod(fixedSamples(0.5d), 1));
    }

    private static MonteCarloMethod fixedSamples(double... values) {
        return context -> {
            List<Num> samples = new ArrayList<>(values.length);
            for (double value : values) {
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
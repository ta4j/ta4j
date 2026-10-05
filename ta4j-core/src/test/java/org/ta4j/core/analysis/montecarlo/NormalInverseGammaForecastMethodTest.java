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
import org.ta4j.core.TestUtils;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the Normal-Inverse-Gamma posterior-predictive technique against
 * independently derived closed-form predictive moments.
 */
public class NormalInverseGammaForecastMethodTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    private static final double[] WINDOW = { 0.012, -0.008, 0.02, -0.015, 0.005, 0.03, -0.022, 0.011, -0.004, 0.017,
            -0.03, 0.009, 0.002, -0.012, 0.024, -0.007 };

    @Test
    public void oneStepPredictiveMomentsMatchClosedForm() {
        MonteCarloContext context = context(1, 400_000, window(), new SplittableRandom(20_260_824L));
        NormalInverseGammaForecastMethod method = new NormalInverseGammaForecastMethod(0d, 1d, 2d, 0d);

        List<Num> samples = method.terminalReturns(context);

        int n = WINDOW.length;
        double mean = 0d;
        for (double value : WINDOW) {
            mean += value;
        }
        mean /= n;
        double squaredDeviationSum = 0d;
        for (double value : WINDOW) {
            squaredDeviationSum += (value - mean) * (value - mean);
        }
        double kn = 1d + n;
        double mn = n * mean / kn;
        double an = 2d + n / 2.0;
        double bn = squaredDeviationSum / 2.0 + 1d * n * mean * mean / (2.0 * kn);
        // One-step predictive is Student-t with variance scale^2 * nu / (nu - 2)
        // which collapses to bn * (kn + 1) / (kn * (an - 1)).
        double expectedVariance = bn * (kn + 1) / (kn * (an - 1));

        double sampleMean = 0d;
        for (Num sample : samples) {
            sampleMean += sample.bigDecimalValue().doubleValue();
        }
        sampleMean /= samples.size();
        double sampleVariance = 0d;
        for (Num sample : samples) {
            double deviation = sample.bigDecimalValue().doubleValue() - sampleMean;
            sampleVariance += deviation * deviation;
        }
        sampleVariance /= samples.size();

        TestUtils.assertNumEquals(FACTORY.numOf(mn), FACTORY.numOf(sampleMean), 1e-4);
        TestUtils.assertNumEquals(FACTORY.numOf(expectedVariance), FACTORY.numOf(sampleVariance),
                expectedVariance * 0.05);
    }

    @Test
    public void sameSeedReproducesIdenticalSamples() {
        NormalInverseGammaForecastMethod method = NormalInverseGammaForecastMethod.withEmpiricalPriors();
        List<Num> firstRun = method.terminalReturns(context(3, 200, window(), new SplittableRandom(42L)));
        List<Num> secondRun = method.terminalReturns(context(3, 200, window(), new SplittableRandom(42L)));

        assertEquals(firstRun.size(), secondRun.size());
        for (int i = 0; i < firstRun.size(); i++) {
            assertEquals(firstRun.get(i), secondRun.get(i));
        }
    }

    @Test
    public void constantWindowCollapsesToDeterministicDriftAccumulation() {
        double constantReturn = 0.01;
        List<Num> constantWindow = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            constantWindow.add(FACTORY.numOf(constantReturn));
        }
        NormalInverseGammaForecastMethod method = NormalInverseGammaForecastMethod.withEmpiricalPriors();

        List<Num> samples = method.terminalReturns(context(3, 10, constantWindow, new SplittableRandom(7L)));

        assertEquals(10, samples.size());
        for (Num sample : samples) {
            TestUtils.assertNumEquals(FACTORY.numOf(3 * constantReturn), sample, 1e-12);
        }
    }

    @Test
    public void emptyWindowYieldsNoSamples() {
        assertNull(NormalInverseGammaForecastMethod.withEmpiricalPriors()
                .terminalReturns(context(1, 5, List.of(), new SplittableRandom(1L))));
    }

    @Test
    public void rejectsInvalidPriorHyperparameters() {
        assertThrows(IllegalArgumentException.class, () -> new NormalInverseGammaForecastMethod(0d, 0d, 2d, 0d));
        assertThrows(IllegalArgumentException.class, () -> new NormalInverseGammaForecastMethod(0d, -1d, 2d, 0d));
        assertThrows(IllegalArgumentException.class, () -> new NormalInverseGammaForecastMethod(0d, 1d, 0d, 0d));
        assertThrows(IllegalArgumentException.class, () -> new NormalInverseGammaForecastMethod(0d, 1d, 2d, -0.5d));
        assertThrows(IllegalArgumentException.class,
                () -> new NormalInverseGammaForecastMethod(Double.NaN, 1d, 2d, 0d));
    }

    private static List<Num> window() {
        List<Num> values = new ArrayList<>(WINDOW.length);
        for (double value : WINDOW) {
            values.add(FACTORY.numOf(value));
        }
        return values;
    }

    private static MonteCarloContext context(int horizon, int iterationCount, List<Num> historicalLogReturns,
            SplittableRandom random) {
        ReturnMoments moments = ReturnMoments.stable(100, Math.max(1, historicalLogReturns.size()),
                ReturnRepresentation.LOG, FACTORY.zero(), FACTORY.zero(), FACTORY.one());
        return new MonteCarloContext(100, horizon, iterationCount, historicalLogReturns, moments, random, FACTORY);
    }
}

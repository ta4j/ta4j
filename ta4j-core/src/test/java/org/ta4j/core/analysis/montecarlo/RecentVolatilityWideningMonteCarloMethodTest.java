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
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the recent-volatility widening decorator: calm windows are left
 * untouched, wilder windows are widened around the drift path with the ratio
 * capped at the configured bound, and the seam null/wrong-count contract is
 * honored.
 */
public class RecentVolatilityWideningMonteCarloMethodTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    @Test
    public void calmWindowIsNotWidened() {
        // Recent RMS 0.2 vs state volatility 1.0 -> factor max(1, 0.2) = 1.
        MonteCarloMethod inner = fixedSamples(0.5d, 1.5d);
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(inner, 2, 4d);

        List<Num> samples = method.terminalReturns(context(2, 2, window(0.2d, 0.2d), moments(1d), 1L));

        assertEquals(2, samples.size());
        TestUtils.assertNumEquals(FACTORY.numOf(0.5d), samples.get(0), 1e-12);
        TestUtils.assertNumEquals(FACTORY.numOf(1.5d), samples.get(1), 1e-12);
    }

    @Test
    public void wilderRecentWindowWidensAroundSampleMean() {
        // Recent RMS 0.2 over 2 bars vs state volatility 0.05 -> ratio 4, capped at 4.
        // Inner samples [0, 1] have center 0.5: 0.5 + 4 * (0 - 0.5) = -1.5,
        // 0.5 + 4 * (1 - 0.5) = 2.5. Widening must not shift the center.
        MonteCarloMethod inner = fixedSamples(0.0d, 1.0d);
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(inner, 2, 4d);

        List<Num> samples = method.terminalReturns(context(2, 2, window(0.2d, 0.2d), moments(0.05d, 0.05d), 1L));

        assertEquals(2, samples.size());
        TestUtils.assertNumEquals(FACTORY.numOf(-1.5d), samples.get(0), 1e-12);
        TestUtils.assertNumEquals(FACTORY.numOf(2.5d), samples.get(1), 1e-12);
    }

    @Test
    public void capIsAppliedWhenRatioExceedsMaxWiden() {
        // Recent RMS 0.4 vs state volatility 0.01 -> ratio 40, capped at 3.
        // Inner samples [0, 1] have center 0.5: 0.5 + 3 * (0 - 0.5) = -1.0,
        // 0.5 + 3 * (1 - 0.5) = 2.0.
        MonteCarloMethod inner = fixedSamples(0.0d, 1.0d);
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(inner, 2, 3d);

        List<Num> samples = method.terminalReturns(context(2, 2, window(0.4d, 0.4d), moments(0.01d, 0.05d), 1L));

        assertEquals(2, samples.size());
        TestUtils.assertNumEquals(FACTORY.numOf(-1.0d), samples.get(0), 1e-12);
        TestUtils.assertNumEquals(FACTORY.numOf(2.0d), samples.get(1), 1e-12);
    }

    @Test
    public void sameSeedReproducesIdenticalSamples() {
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(
                NormalInverseGammaForecastMethod.withEmpiricalPriors());
        List<Num> firstRun = method.terminalReturns(context(3, 200, window(0.2d, 0.2d), moments(0.05d), 42L));
        List<Num> secondRun = method.terminalReturns(context(3, 200, window(0.2d, 0.2d), moments(0.05d), 42L));

        assertEquals(firstRun.size(), secondRun.size());
        for (int i = 0; i < firstRun.size(); i++) {
            assertEquals(firstRun.get(i), secondRun.get(i));
        }
    }

    @Test
    public void nullInnerPropagatesAsUnstable() {
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(context -> null);

        assertNull(method.terminalReturns(context(2, 5, window(0.2d, 0.2d), moments(0.05d), 1L)));
    }

    @Test
    public void wrongCountInnerPropagatesAsUnstable() {
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(fixedSamples(0.5d));

        assertNull(method.terminalReturns(context(2, 3, window(0.2d, 0.2d), moments(0.05d), 1L)));
    }

    @Test
    public void unstableMomentsPropagateAsUnstable() {
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(fixedSamples(0.5d));
        ReturnMoments unstable = ReturnMoments.unstable(100, 2, ReturnRepresentation.LOG);

        assertNull(method.terminalReturns(context(2, 1, window(0.2d, 0.2d), unstable, 1L)));
    }

    @Test
    public void zeroStateVolatilityPropagatesAsUnstable() {
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(fixedSamples(0.5d));

        assertNull(method.terminalReturns(context(2, 1, window(0.2d, 0.2d), moments(0d), 1L)));
    }

    @Test
    public void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new RecentVolatilityWideningMonteCarloMethod(null));
        assertThrows(IllegalArgumentException.class,
                () -> new RecentVolatilityWideningMonteCarloMethod(fixedSamples(0.5d), 1, 4d));
        assertThrows(IllegalArgumentException.class,
                () -> new RecentVolatilityWideningMonteCarloMethod(fixedSamples(0.5d), 2, 0.5d));
        assertThrows(IllegalArgumentException.class,
                () -> new RecentVolatilityWideningMonteCarloMethod(fixedSamples(0.5d), 2, Double.NaN));
    }

    @Test
    public void hugeDecimalWindowWidensInsteadOfOverflowing() {
        // Returns ~1e160 in a BigDecimal context: primitive-domain squaring would
        // overflow to infinity and the decorator would return null, but the
        // Num-domain RMS stays finite, so the factor (capped at 4) widens instead.
        MonteCarloMethod inner = fixedSamples(0.0d, 1.0d);
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(inner, 2, 4d);

        List<Num> samples = method
                .terminalReturns(decimalContext(2, 2, decimalWindow(1e160, 1e160), decimalMoments(1e-4, 0d), 1L));

        assertEquals(2, samples.size());
        assertEquals(0, samples.stream().filter(s -> !Num.isFinite(s)).count());
        TestUtils.assertNumEquals(DECIMAL.numOf(-1.5d), samples.get(0), 1e-6);
        TestUtils.assertNumEquals(DECIMAL.numOf(2.5d), samples.get(1), 1e-6);
    }

    @Test
    public void tinyDecimalWindowDoesNotUnderflow() {
        // Returns ~1e-200 against a state volatility ~1e-210: in double, squaring
        // the window returns underflows to zero and the ratio collapses to 1 (no
        // widening); in BigDecimal the squares stay representable so the ratio
        // exceeds the cap and the samples are widened around their mean.
        ReturnMoments tinyVol = ReturnMoments.stable(100, 2, ReturnRepresentation.LOG, DECIMAL.zero(),
                DECIMAL.numOf(0d), DECIMAL.numOf(new java.math.BigDecimal("1E-420")));
        MonteCarloMethod inner = fixedSamples(0.0d, 1.0d);
        MonteCarloMethod method = new RecentVolatilityWideningMonteCarloMethod(inner, 2, 4d);

        List<Num> samples = method.terminalReturns(decimalContext(1, 2, decimalWindow(1e-200, 1e-200), tinyVol, 1L));

        assertEquals(2, samples.size());
        TestUtils.assertNumEquals(DECIMAL.numOf(-1.5d), samples.get(0), 1e-6);
        TestUtils.assertNumEquals(DECIMAL.numOf(2.5d), samples.get(1), 1e-6);
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

    private static ReturnMoments moments(double variance) {
        return moments(variance, 0d);
    }

    private static ReturnMoments moments(double variance, double drift) {
        return ReturnMoments.stable(100, 2, ReturnRepresentation.LOG, FACTORY.zero(), FACTORY.numOf(drift),
                FACTORY.numOf(variance * variance));
    }

    private static MonteCarloContext context(int horizon, int iterationCount, List<Num> historicalLogReturns,
            ReturnMoments moments, long seed) {
        return new MonteCarloContext(100, horizon, iterationCount, historicalLogReturns, moments,
                new SplittableRandom(seed), FACTORY);
    }

    private static final NumFactory DECIMAL = DecimalNumFactory.getInstance();

    private static List<Num> decimalWindow(double... values) {
        List<Num> result = new ArrayList<>(values.length);
        for (double value : values) {
            result.add(DECIMAL.numOf(value));
        }
        return result;
    }

    private static ReturnMoments decimalMoments(double volatility, double drift) {
        return ReturnMoments.stable(100, 2, ReturnRepresentation.LOG, DECIMAL.zero(), DECIMAL.numOf(drift),
                DECIMAL.numOf(volatility * volatility));
    }

    private static MonteCarloContext decimalContext(int horizon, int iterationCount, List<Num> historicalLogReturns,
            ReturnMoments moments, long seed) {
        return new MonteCarloContext(100, horizon, iterationCount, historicalLogReturns, moments,
                new SplittableRandom(seed), DECIMAL);
    }
}
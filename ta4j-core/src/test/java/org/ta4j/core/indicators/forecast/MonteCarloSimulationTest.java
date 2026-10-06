/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloTestFixtures.FixedReturnIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloTestFixtures.FixedReturnStateIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Owns the forecast RNG stream contract: the per-path stream matches its golden
 * vectors, and seeded {@link MonteCarloPriceForecastIndicator} forecasts draw
 * every path from its own stream.
 */
public class MonteCarloSimulationTest {

    private static final List<Num> HISTORY = List.of(numOf(Math.log(0.9d)), numOf(Math.log(1.1d)));

    @Test
    public void publicRawStateAndMixedOutputFollowPerPathStream() {
        long state = MonteCarloKernel.initialPathState(42L, 317, 12, 5);
        MonteCarloSimulation.DeterministicRandom random = stream();
        for (long expected : new long[] { 0xacdefb464966b93cL, 0x6c87c018610d701aL, 0x85b233fcd16e891cL }) {
            state = MonteCarloKernel.advanceState(state);
            assertEquals(expected, MonteCarloKernel.mix64(state));
            assertEquals(expected, random.nextLong());
        }
    }

    @Test
    public void boundedSelectionMatchesPerPathGoldenVectors() {
        assertVector(1, 0, 0, 0, 0, 0, 0, 0, 0);
        assertVector(2, 0, 1, 0, 0, 1, 0, 0, 1);
        assertVector(7, 2, 3, 2, 5, 1, 5, 1, 5);
        assertVector(252, 170, 241, 114, 96, 57, 152, 162, 47);
        assertVector(256, 158, 13, 142, 100, 217, 36, 98, 167);
        assertVector(1_000, 990, 181, 814, 188, 65, 908, 682, 759);
    }

    @Test
    public void gaussianSelectionMatchesPerPathGoldenVector() {
        MonteCarloSimulation.DeterministicRandom random = stream();

        double[] actual = new double[6];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = random.nextGaussian();
        }

        assertArrayEquals(new double[] { -1.3318445490451813, 0.5448539398879264, -0.5868281460745287,
                0.2513242949628345, -0.6323990089329744, -1.3505535293708895 }, actual, 0d);
    }

    @Test
    public void pathCoordinatesAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloSimulation.DeterministicRandom.forPath(1L, -1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloSimulation.DeterministicRandom.forPath(1L, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloSimulation.DeterministicRandom.forPath(1L, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> stream().nextInt(0));
    }

    @Test
    public void seededForecastsDrawEachPathFromItsOwnStream() {
        assertForecastMatches(replay(), indicator().getValue(2));
    }

    private static MonteCarloPriceForecastIndicator indicator() {
        BarSeries series = constantSeries(3, 100d);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0d),
                HISTORY.get(0), HISTORY.get(1));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        return MonteCarloPriceForecastIndicator.builder(new ClosePriceIndicator(series), state)
                .horizon(2)
                .iterationCount(4)
                .lookbackBarCount(2)
                .seed(3L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .quantiles(0.0, 0.5, 1.0)
                .build();
    }

    /**
     * Replays the historical bootstrap outside the engine, drawing each path from
     * its own per-path stream.
     */
    private static Forecast replay() {
        List<Num> terminals = new ArrayList<>();
        for (int path = 0; path < 4; path++) {
            RandomGenerator random = MonteCarloSimulation.DeterministicRandom.forPath(3L, 2, 2, path);
            double cumulative = 0d;
            for (int step = 0; step < 2; step++) {
                cumulative += HISTORY.get(random.nextInt(2)).doubleValue();
            }
            terminals.add(numOf(100d * Math.exp(cumulative)));
        }
        return Forecast.ofSamples(2, 2, terminals, List.of(0.0, 0.5, 1.0));
    }

    private static void assertForecastMatches(Forecast expected, Forecast actual) {
        assertTrue("fixture must be stable", actual.isStable());
        assertEquals(expected.support(), actual.support());
        assertEquals(expected.sampleCount(), actual.sampleCount());
        assertEquals(expected.mean().doubleValue(), actual.mean().doubleValue(), 0d);
        assertEquals(expected.median().doubleValue(), actual.median().doubleValue(), 0d);
        assertEquals(expected.standardDeviation().doubleValue(), actual.standardDeviation().doubleValue(), 0d);
        for (Double probability : expected.quantiles().keySet()) {
            assertEquals(expected.quantile(probability).doubleValue(), actual.quantile(probability).doubleValue(), 0d);
        }
    }

    private static Num numOf(double value) {
        return DoubleNumFactory.getInstance().numOf(value);
    }

    private static BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(values).build();
    }

    private static void assertVector(int bound, int... expected) {
        MonteCarloSimulation.DeterministicRandom random = stream();
        int[] actual = new int[expected.length];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = random.nextInt(bound);
        }
        assertArrayEquals(expected, actual);
    }

    private static MonteCarloSimulation.DeterministicRandom stream() {
        return MonteCarloSimulation.DeterministicRandom.forPath(42L, 317, 12, 5);
    }
}

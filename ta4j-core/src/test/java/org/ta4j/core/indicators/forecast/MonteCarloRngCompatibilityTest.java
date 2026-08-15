/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.After;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Locks the numeric compatibility contract of the public
 * {@link MonteCarloPriceForecastIndicator}: seeded forecasts that were
 * reproducible with the pre-0.23.1 {@link SplittableRandom} stream must remain
 * reproducible by default, exactly as they were in earlier releases, while RNG
 * version {@code 1} selects the deterministic per-path stream used for native
 * parity.
 */
public class MonteCarloRngCompatibilityTest {

    @After
    public void clearRngVersion() {
        System.clearProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY);
    }

    @Test
    public void legacyRngVersionReproducesPreUpgradeForecastValues() {
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "0");

        assertForecastMatches(legacyExpectedForecast(), indicator().getValue(2));
    }

    @Test
    public void defaultRngVersionReproducesPreUpgradeForecastValues() {
        assertForecastMatches(legacyExpectedForecast(), indicator().getValue(2));
    }

    @Test
    public void perPathRngVersionSelectsTheDeterministicStream() {
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "1");

        Forecast actual = indicator().getValue(2);
        Forecast legacy = legacyExpectedForecast();

        // Version 1 is the deterministic per-path stream, observably different
        // from the historical shared stream.
        assertTrue(actual.isStable());
        assertFalse(legacy.median().doubleValue() == actual.median().doubleValue());
    }

    @Test
    public void invalidRngVersionIsRejected() {
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "2");

        assertThrows(IllegalArgumentException.class, () -> indicator().getValue(2));
    }

    private static MonteCarloPriceForecastIndicator indicator() {
        BarSeries series = constantSeries(3, 100d);
        Indicator<Num> close = new ClosePriceIndicator(series);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0d),
                numOf(Math.log(0.9d)), numOf(Math.log(1.1d)));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(2)
                .iterationCount(4)
                .lookbackBarCount(2)
                .seed(3L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .quantiles(0.0, 0.5, 1.0)
                .build();
    }

    /**
     * Replays the exact historical algorithm: one shared {@link SplittableRandom}
     * seeded with the legacy {@code mixSeed} derivation for the whole decision,
     * bootstrapping from the same historical returns.
     */
    private static Forecast legacyExpectedForecast() {
        List<Num> history = List.of(numOf(Math.log(0.9d)), numOf(Math.log(1.1d)));
        SplittableRandom random = new SplittableRandom(mixSeed(3L, 2, 2));
        List<Num> terminals = new ArrayList<>();
        for (int iteration = 0; iteration < 4; iteration++) {
            double cumulative = 0d;
            for (int step = 0; step < 2; step++) {
                cumulative += history.get(random.nextInt(2)).doubleValue();
            }
            terminals.add(numOf(100d * Math.exp(cumulative)));
        }
        return Forecast.ofSamples(2, 2, terminals, List.of(0.0, 0.5, 1.0));
    }

    private static void assertForecastMatches(Forecast legacy, Forecast actual) {
        assertTrue("legacy fixture must be stable", actual.isStable());
        assertEquals(legacy.support(), actual.support());
        assertEquals(legacy.horizon(), actual.horizon());
        assertEquals(legacy.sampleCount(), actual.sampleCount());
        assertEquals(legacy.mean().doubleValue(), actual.mean().doubleValue(), 1e-9);
        assertEquals(legacy.median().doubleValue(), actual.median().doubleValue(), 1e-9);
        assertEquals(legacy.standardDeviation().doubleValue(), actual.standardDeviation().doubleValue(), 1e-9);
        for (Double probability : legacy.quantiles().keySet()) {
            assertEquals(legacy.quantile(probability).doubleValue(), actual.quantile(probability).doubleValue(), 1e-9);
        }
    }

    private static long mixSeed(long seed, int index, int horizon) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L + ((long) index << 32) + index;
        value = Long.rotateLeft(value, 27) * 0x3C79AC492BA7B653L;
        value ^= 0x1C69B3F74AC4AE35L + horizon;
        value = Long.rotateLeft(value, 31) * 0x1C69B3F74AC4AE35L;
        return value ^ value >>> 33;
    }

    private static Num numOf(double value) {
        return DoubleNumFactory.getInstance().numOf(value);
    }

    private static BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        java.util.Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(values).build();
    }

    private static final class FixedReturnIndicator extends FixedIndicator<Num> implements ReturnIndicator {

        private final ReturnRepresentation representation;

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, Num... values) {
            super(series, values);
            this.representation = representation;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }
    }

    private static final class FixedReturnStateIndicator implements ReturnForecastStateIndicator<ReturnForecastState> {

        private final ReturnIndicator returns;
        private final ReturnRepresentation representation;

        private FixedReturnStateIndicator(ReturnIndicator returns, ReturnRepresentation representation) {
            this.returns = returns;
            this.representation = representation;
        }

        @Override
        public ReturnIndicator getReturnIndicator() {
            return returns;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public ReturnForecastState getValue(int index) {
            Num zero = getBarSeries().numFactory().zero();
            return ReturnForecastState.stable(index, index + 1, representation, zero, zero, zero);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return returns.getBarSeries();
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.PlannedOperation;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

public class MonteCarloShockPathPlannerTest {

    private static final double DOWN = Math.log(0.9);
    private static final double UP = Math.log(1.1);

    @Before
    public void selectPerPathRng() {
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "1");
    }

    @After
    public void clearPerPathRng() {
        System.clearProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY);
    }

    @Test
    public void snapshotsScalarInputsExactly() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        PlannedOperation planned = new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 3,
                fixture.series.numFactory());

        AccelerationRuntime.KernelRequest request = planned.request();
        assertEquals(AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1, request.operation());
        assertEquals(2, request.fromInclusive());
        assertEquals(3, request.toInclusive());
        assertEquals(2, request.outputsPerIndex());
        assertArrayEquals(new double[] { 100d, 100d }, request.inputs().get(MonteCarloKernel.INPUT_PRICES), 0d);
        assertArrayEquals(new double[] { DOWN, UP, UP, 0d }, request.inputs().get(MonteCarloKernel.INPUT_WINDOWS), 0d);
        double[] params = request.params();
        assertEquals(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP.ordinal(), params[0], 0d);
        assertEquals(1d, params[2], 0d);
        assertEquals(2d, params[3], 0d);
        assertEquals(2d, params[4], 0d);
        assertTrue(request.estimatedScalarNanos() > 0);
        assertTrue(request.peakDeviceBytesEstimate() > 0);
    }

    @Test
    public void declinesIncompleteWindows() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        assertNull(new MonteCarloShockPathPlanner().plan(fixture.indicator, 0, 1, fixture.series.numFactory()));
    }

    @Test
    public void declinesNonDoubleNumerics() {
        Fixture fixture = fixture(DecimalNumFactory.getInstance());

        assertNull(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory()));
    }

    @Test
    public void declinesLegacyRandomStreams() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "0");
        try {
            assertNull(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory()));
        } finally {
            System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "1");
        }
    }

    @Test
    public void ignoresUnclaimedIndicators() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        assertNull(new MonteCarloShockPathPlanner().plan(new ClosePriceIndicator(fixture.series), 2, 2,
                fixture.series.numFactory()));
    }

    @Test
    public void declinesCustomMonteCarloMethod() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        BarSeries series = fixture.series;
        Indicator<Num> close = new ClosePriceIndicator(series);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG,
                series.numFactory().numOf(0), series.numFactory().numOf(DOWN), series.numFactory().numOf(UP),
                series.numFactory().numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator indicator = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .monteCarloMethod(context -> List.of())
                .build();

        assertNull(new MonteCarloShockPathPlanner().plan(indicator, 2, 3, series.numFactory()));
    }

    private static Fixture fixture(org.ta4j.core.num.NumFactory factory) {
        double[] prices = new double[4];
        Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(prices).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, factory.numOf(0),
                factory.numOf(DOWN), factory.numOf(UP), factory.numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator indicator = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(2)
                .seed(3L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .quantiles(0.0, 0.5, 1.0)
                .build();
        return new Fixture(series, indicator);
    }

    private record Fixture(BarSeries series, MonteCarloPriceForecastIndicator indicator) {
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

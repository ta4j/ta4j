/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.PlanAttempt;
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
import org.ta4j.core.num.NumFactory;

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

        PlanAttempt attempt = new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 3,
                fixture.series.numFactory(), Long.MAX_VALUE);

        assertTrue(attempt.isPlanned());
        AccelerationRuntime.KernelRequest request = attempt.operation().request();
        assertEquals(AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1, request.operation());
        assertEquals(2, request.fromInclusive());
        assertEquals(3, request.toInclusive());
        assertEquals(2, request.outputsPerIndex());
        assertArrayEquals(new double[] { 100d, 100d }, request.inputs().get(MonteCarloKernel.INPUT_PRICES), 0d);
        assertArrayEquals(new double[] { DOWN, UP, UP, 0d }, request.inputs().get(MonteCarloKernel.INPUT_WINDOWS), 0d);
        request.inputs().get(MonteCarloKernel.INPUT_PRICES)[0] = -1d;
        assertEquals(100d, request.inputs().get(MonteCarloKernel.INPUT_PRICES)[0], 0d);
        double[] params = request.params();
        assertEquals(0d, params[0], 0d);
        assertEquals(1d, params[2], 0d);
        assertEquals(2d, params[3], 0d);
        assertEquals(2d, params[4], 0d);
        assertTrue(request.estimatedScalarNanos() > 0);
        assertTrue(request.peakDeviceBytesEstimate() > 0);
    }

    @Test
    public void declinesPlansExceedingConfiguredMemoryBudget() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        assertIneligible(
                new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 3, fixture.series.numFactory(), 1L), 3);
    }

    @Test
    public void hostStagingBudgetIsIndependentOfDeviceBudget() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        MonteCarloShockPathPlanner planner = new MonteCarloShockPathPlanner();

        assertIneligible(planner.plan(fixture.indicator, 2, 3, fixture.series.numFactory(), Long.MAX_VALUE, 300L), 3);
        assertTrue(
                planner.plan(fixture.indicator, 2, 3, fixture.series.numFactory(), Long.MAX_VALUE, 1024L).isPlanned());
    }

    @Test
    public void declinesUnrepresentableWindowDimensionsWithoutAllocating() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(fixture.indicator.kernelPriceIndicator(), fixture.indicator.kernelStateIndicator())
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(Integer.MAX_VALUE)
                .build();

        assertIneligible(new MonteCarloShockPathPlanner().plan(forecast, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
                fixture.series.numFactory(), Long.MAX_VALUE), Integer.MAX_VALUE);
    }

    @Test
    public void declinesRangesThatEndBeforeTheFirstCompleteWindow() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        assertIneligible(new MonteCarloShockPathPlanner().plan(fixture.indicator, 0, 0, fixture.series.numFactory(),
                Long.MAX_VALUE), 1);
    }

    @Test
    public void preservesTheUnavailablePrefixWhenPlanning() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        PlanAttempt attempt = new MonteCarloShockPathPlanner().plan(fixture.indicator, 0, 3,
                fixture.series.numFactory(), Long.MAX_VALUE);

        assertTrue(attempt.isPlanned());
        assertEquals(1, attempt.operation().request().fromInclusive());
        assertEquals(3, attempt.operation().request().toInclusive());
    }

    @Test
    public void keepsTheScalarStabilityBoundaryOutOfTheBatch() {
        BarSeries series = longSeries();
        NumFactory factory = series.numFactory();
        Num[] values = new Num[300];
        Arrays.fill(values, factory.numOf(UP));
        // A return source whose unstable prefix is still finite: only the canonical
        // stability boundary keeps origin 251 out of an accelerated batch.
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, 1, values);
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(252)
                .seed(3L)
                .build();
        MonteCarloShockPathPlanner planner = new MonteCarloShockPathPlanner();

        assertEquals(1, returns.getCountOfUnstableBars());
        assertEquals(252, forecast.getCountOfUnstableBars());
        assertIneligible(planner.plan(forecast, 250, 251, factory, Long.MAX_VALUE), 252);

        PlanAttempt attempt = planner.plan(forecast, 251, 299, factory, Long.MAX_VALUE);
        assertTrue(attempt.isPlanned());
        assertEquals(252, attempt.operation().request().fromInclusive());
        assertEquals(299, attempt.operation().request().toInclusive());
    }

    @Test
    public void declinesNonDoubleNumerics() {
        Fixture fixture = fixture(DecimalNumFactory.getInstance());

        assertUnclaimed(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory(),
                Long.MAX_VALUE));
    }

    @Test
    public void declinesLegacyRandomStreams() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "0");
        try {
            assertUnclaimed(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory(),
                    Long.MAX_VALUE));
        } finally {
            System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "1");
        }
    }

    @Test
    public void ignoresUnclaimedIndicators() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        assertUnclaimed(new MonteCarloShockPathPlanner().plan(new ClosePriceIndicator(fixture.series), 2, 2,
                fixture.series.numFactory(), Long.MAX_VALUE));
    }

    @Test
    public void declinesCustomMonteCarloMethods() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance(), true);
        assertUnclaimed(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory(),
                Long.MAX_VALUE));
    }

    private static void assertIneligible(PlanAttempt attempt, int retryFromIndex) {
        assertFalse(attempt.isPlanned());
        assertFalse(attempt.decline().permanent());
        assertEquals(retryFromIndex, attempt.decline().retryFromIndex());
    }

    private static void assertUnclaimed(PlanAttempt attempt) {
        assertFalse(attempt.isPlanned());
        assertTrue(attempt.decline().permanent());
    }

    private static BarSeries longSeries() {
        double[] prices = new double[300];
        Arrays.fill(prices, 100d);
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices).build();
    }

    private static Fixture fixture(org.ta4j.core.num.NumFactory factory) {
        return fixture(factory, false);
    }

    private static Fixture fixture(org.ta4j.core.num.NumFactory factory, boolean customMethod) {
        double[] prices = new double[4];
        Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(prices).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, factory.numOf(0),
                factory.numOf(DOWN), factory.numOf(UP), factory.numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator.Builder builder = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(2)
                .seed(3L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .quantiles(0.0, 0.5, 1.0);
        if (customMethod) {
            builder.monteCarloMethod(context -> List.of(context.numFactory().zero(), context.numFactory().zero()));
        }
        return new Fixture(series, builder.build());
    }

    private record Fixture(BarSeries series, MonteCarloPriceForecastIndicator indicator) {
    }

    private static final class FixedReturnIndicator extends FixedIndicator<Num> implements ReturnIndicator {

        private final ReturnRepresentation representation;
        private final int unstableBars;

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, Num... values) {
            this(series, representation, 0, values);
        }

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, int unstableBars,
                Num... values) {
            super(series, values);
            this.representation = representation;
            this.unstableBars = unstableBars;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
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

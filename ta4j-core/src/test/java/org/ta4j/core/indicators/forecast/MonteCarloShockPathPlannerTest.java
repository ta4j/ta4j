/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.PlanAttempt;
import org.ta4j.core.acceleration.PlanDecline;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloTestFixtures.FixedReturnIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloTestFixtures.FixedReturnStateIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
        // Rows 2 and 3 share one contiguous buffer: row r reads returns[r .. r + 1].
        assertArrayEquals(new double[] { DOWN, UP, 0d }, request.inputs().get(MonteCarloKernel.INPUT_RETURNS), 0d);
        double[] params = request.params();
        assertEquals(MonteCarloKernel.PARAM_COUNT, params.length);
        assertEquals(MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP, params[MonteCarloKernel.PARAM_SHOCK_MODEL], 0d);
        assertEquals(MonteCarloKernel.VOLATILITY_CONSTANT, params[MonteCarloKernel.PARAM_VOLATILITY_MODE], 0d);
        assertEquals(1d, params[MonteCarloKernel.PARAM_HORIZON], 0d);
        assertEquals(2d, params[MonteCarloKernel.PARAM_ITERATIONS], 0d);
        assertEquals(2d, params[MonteCarloKernel.PARAM_LOOKBACK], 0d);
        assertEquals(MonteCarloKernel.smoothingBandwidthFactor(2), params[MonteCarloKernel.PARAM_SMOOTHING_FACTOR], 0d);
        assertTrue(request.estimatedScalarNanos() > 0);
        assertTrue(request.peakDeviceBytesEstimate() > 0);
    }

    @Test
    public void snapshotsNegativeZeroReturnsAsScalarNormalizationDoes() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Fixture fixture = fixture(factory, false, factory.numOf(0), factory.numOf(-0.0d), factory.numOf(UP));

        PlanAttempt attempt = new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, factory, Long.MAX_VALUE);

        double[] windows = attempt.operation().request().inputs().get(MonteCarloKernel.INPUT_RETURNS);
        assertEquals(0L, Double.doubleToRawLongBits(windows[0]));
        assertEquals(UP, windows[1], 0d);
    }

    @Test
    public void declinesPermanentlyWhenOneDecisionIndexExceedsTheDeviceBudget() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        PlanAttempt attempt = new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 3,
                fixture.series.numFactory(), 1L);

        assertPermanent(attempt, "device");
    }

    @Test
    public void lowersTheLongestPrefixThatFitsTheHostBudget() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        MonteCarloShockPathPlanner planner = new MonteCarloShockPathPlanner();
        NumFactory factory = fixture.series.numFactory();

        // One row of this fixture stages 184 host bytes plus 24 for the shared return.
        PlanAttempt oneRow = planner.plan(fixture.indicator, 2, 3, factory, Long.MAX_VALUE, 300L);
        assertTrue(oneRow.isPlanned());
        assertEquals(2, oneRow.operation().request().fromInclusive());
        assertEquals(2, oneRow.operation().request().toInclusive());

        PlanAttempt fullRange = planner.plan(fixture.indicator, 2, 3, factory, Long.MAX_VALUE, 1024L);
        assertEquals(3, fullRange.operation().request().toInclusive());

        assertPermanent(planner.plan(fixture.indicator, 2, 3, factory, Long.MAX_VALUE, 100L), "host");
    }

    @Test
    public void declinesUnrepresentableWindowDimensionsWithoutAllocating() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        MonteCarloPriceForecastIndicator.ShockPathKernelConfig config = fixture.indicator.shockPathKernelConfig();
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(config.priceIndicator(), config.stateIndicator())
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(Integer.MAX_VALUE)
                .build();

        assertPermanent(new MonteCarloShockPathPlanner().plan(forecast, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
                fixture.series.numFactory(), Long.MAX_VALUE), "one decision index");
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
    public void truncatesTheBatchBeforeTheFirstWindowWithANonFiniteReturn() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Fixture fixture = fixture(factory, false, factory.numOf(0), factory.numOf(DOWN), factory.numOf(UP),
                factory.numOf(Double.NaN), factory.numOf(UP), factory.numOf(DOWN));
        MonteCarloShockPathPlanner planner = new MonteCarloShockPathPlanner();

        PlanAttempt prefix = planner.plan(fixture.indicator, 1, 5, factory, Long.MAX_VALUE);
        assertTrue(prefix.isPlanned());
        assertEquals(1, prefix.operation().request().fromInclusive());
        assertEquals(2, prefix.operation().request().toInclusive());

        // Origins 3 and 4 both read the NaN at index 3; retry after the last one.
        assertIneligible(planner.plan(fixture.indicator, 3, 5, factory, Long.MAX_VALUE), 5);
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
    public void decodesLogReturnsThroughTheScalarTerminalPriceGuards() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        NumFactory factory = fixture.series.numFactory();
        PlanAttempt attempt = new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, factory, Long.MAX_VALUE);

        Forecast nonFinite = (Forecast) attempt.operation()
                .decoder()
                .decode(new double[] { Double.NaN, 0d }, 2, factory);
        Forecast beyondExponentLimit = (Forecast) attempt.operation()
                .decoder()
                .decode(new double[] { -701d, 0d }, 2, factory);
        Forecast finite = (Forecast) attempt.operation().decoder().decode(new double[] { -0.1d, 0.1d }, 2, factory);

        assertFalse(nonFinite.isStable());
        assertFalse(beyondExponentLimit.isStable());
        assertTrue(finite.isStable());
        assertEquals(factory.numOf(100).multipliedBy(factory.numOf(-0.1d).exp()), finite.quantile(0.0));
        assertEquals(factory.numOf(100).multipliedBy(factory.numOf(0.1d).exp()), finite.quantile(1.0));
    }

    @Test
    public void declinesNonDoubleNumericsWithTheirReason() {
        Fixture fixture = fixture(DecimalNumFactory.getInstance());

        assertPermanent(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory(),
                Long.MAX_VALUE), "DoubleNumFactory");
    }

    @Test
    public void declinesForecastsBuiltWithTheLegacyStreamWithTheirReason() {
        System.setProperty(MonteCarloSimulation.RNG_VERSION_PROPERTY, "0");
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        assertPermanent(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory(),
                Long.MAX_VALUE), MonteCarloSimulation.RNG_VERSION_PROPERTY);
    }

    @Test
    public void declinesCustomMonteCarloMethodsWithTheirReason() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance(), true);

        assertPermanent(new MonteCarloShockPathPlanner().plan(fixture.indicator, 2, 2, fixture.series.numFactory(),
                Long.MAX_VALUE), "MonteCarloMethod");
    }

    @Test
    public void doesNotClaimOtherIndicators() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());

        PlanAttempt attempt = new MonteCarloShockPathPlanner().plan(new ClosePriceIndicator(fixture.series), 2, 2,
                fixture.series.numFactory(), Long.MAX_VALUE);

        assertSame(PlanDecline.unclaimed(), attempt.decline());
    }

    private static void assertIneligible(PlanAttempt attempt, int retryFromIndex) {
        assertFalse(attempt.isPlanned());
        assertFalse(attempt.decline().permanent());
        assertEquals(retryFromIndex, attempt.decline().retryFromIndex());
    }

    private static void assertPermanent(PlanAttempt attempt, String reason) {
        assertFalse(attempt.isPlanned());
        assertTrue(attempt.decline().permanent());
        assertTrue(attempt.decline().detail(), attempt.decline().detail().contains(reason));
    }

    private static BarSeries longSeries() {
        double[] prices = new double[300];
        Arrays.fill(prices, 100d);
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices).build();
    }

    private static Fixture fixture(NumFactory factory) {
        return fixture(factory, false);
    }

    private static Fixture fixture(NumFactory factory, boolean customMethod) {
        return fixture(factory, customMethod, factory.numOf(0), factory.numOf(DOWN), factory.numOf(UP),
                factory.numOf(0));
    }

    private static Fixture fixture(NumFactory factory, boolean customMethod, Num... returnValues) {
        double[] prices = new double[returnValues.length];
        Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(prices).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, returnValues);
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator.Builder builder = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
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
}

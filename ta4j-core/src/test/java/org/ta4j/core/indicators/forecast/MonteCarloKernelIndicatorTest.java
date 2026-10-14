/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloTestFixtures.FixedReturnIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloTestFixtures.FixedReturnStateIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/** Kernel-lane contract of {@link MonteCarloPriceForecastIndicator}. */
public class MonteCarloKernelIndicatorTest {

    private static final double DOWN = Math.log(0.9);
    private static final double UP = Math.log(1.1);

    @Test
    public void snapshotsSpotPriceAndStableMomentsIntoTheirKernelSlots() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        double[] row = new double[fixture.indicator.kernel().inputsPerRow()];

        assertTrue(fixture.indicator.snapshot(2, row));

        ReturnMoments moments = fixture.state.getValue(2).moments();
        assertEquals(100d, row[MonteCarloKernel.INPUT_PRICES], 0d);
        assertEquals(moments.mean().doubleValue(), row[MonteCarloKernel.INPUT_MEANS], 0d);
        assertEquals(moments.drift().doubleValue(), row[MonteCarloKernel.INPUT_DRIFTS], 0d);
        assertEquals(moments.variance().doubleValue(), row[MonteCarloKernel.INPUT_VARIANCES], 0d);
        assertEquals(DOWN, fixture.indicator.windowValue(1), 0d);
        assertEquals(UP, fixture.indicator.windowValue(2), 0d);
    }

    @Test
    public void snapshotsDecimalNumSeriesInDoublePrecision() {
        Fixture fixture = fixture(DecimalNumFactory.getInstance());
        double[] row = new double[fixture.indicator.kernel().inputsPerRow()];

        assertTrue(fixture.indicator.snapshot(2, row));

        assertEquals(100d, row[MonteCarloKernel.INPUT_PRICES], 0d);
        assertEquals(DOWN, fixture.indicator.windowValue(1), 1e-15);
    }

    @Test
    public void windowValuesNormalizeNegativeZeroAndNonFiniteReturns() {
        NumFactory factory = DoubleNumFactory.getInstance();
        Fixture fixture = fixture(factory, factory.numOf(0), factory.numOf(-0.0d), factory.numOf(Double.NaN));

        assertEquals(0L, Double.doubleToRawLongBits(fixture.indicator.windowValue(1)));
        assertTrue(Double.isNaN(fixture.indicator.windowValue(2)));
    }

    @Test
    public void declinesSnapshotsBeforeTheScalarStabilityBoundary() {
        double[] prices = new double[300];
        Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        Num[] values = new Num[300];
        Arrays.fill(values, series.numFactory().numOf(UP));
        // A return source whose unstable prefix is still finite: only the canonical
        // stability boundary keeps origin 251 out of an accelerated batch.
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, 1, values);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series),
                        new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG))
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(252)
                .seed(3L)
                .build();
        double[] row = new double[forecast.kernel().inputsPerRow()];

        assertEquals(252, forecast.getCountOfUnstableBars());
        assertFalse(forecast.snapshot(251, row));
        assertTrue(forecast.snapshot(252, row));
    }

    @Test
    public void decodesLogReturnsThroughTheScalarTerminalPriceGuards() {
        Fixture fixture = fixture(DoubleNumFactory.getInstance());
        NumFactory factory = fixture.series.numFactory();

        Forecast nonFinite = fixture.indicator.decode(2, new double[] { Double.NaN, 0d });
        Forecast beyondExponentLimit = fixture.indicator.decode(2, new double[] { -701d, 0d });
        Forecast finite = fixture.indicator.decode(2, new double[] { -0.1d, 0.1d });

        assertFalse(nonFinite.isStable());
        assertFalse(beyondExponentLimit.isStable());
        assertTrue(finite.isStable());
        assertEquals(factory.numOf(100).multipliedBy(factory.numOf(-0.1d).exp()), finite.quantile(0.0));
        assertEquals(factory.numOf(100).multipliedBy(factory.numOf(0.1d).exp()), finite.quantile(1.0));
    }

    private static Fixture fixture(NumFactory factory) {
        return fixture(factory, factory.numOf(0), factory.numOf(DOWN), factory.numOf(UP), factory.numOf(0));
    }

    private static Fixture fixture(NumFactory factory, Num... returnValues) {
        double[] prices = new double[returnValues.length];
        Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(prices).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, returnValues);
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator indicator = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(2)
                .seed(3L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .quantiles(0.0, 0.5, 1.0)
                .build();
        return new Fixture(series, state, indicator);
    }

    private record Fixture(BarSeries series, FixedReturnStateIndicator state,
            MonteCarloPriceForecastIndicator indicator) {
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastStateIndicatorTest extends AbstractIndicatorTest<LogReturnIndicator, ReturnForecastState> {

    public ForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void initializesRollingMeanStateAfterWarmup() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ForecastStateIndicator stateIndicator = new ForecastStateIndicator(returns, 2, 0.5,
                ForecastStateIndicator.DriftMode.ROLLING_MEAN);

        assertEquals(2, stateIndicator.getCountOfUnstableBars());
        assertTrue(stateIndicator.getValue(1).mean().isNaN());
        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertEquals(2, state.observationCount());
        assertNumEquals(Math.log(1.1), state.mean());
        assertNumEquals(Math.log(1.1), state.drift());
        assertNumEquals(0d, state.variance());
        assertNumEquals(0d, state.volatility());
    }

    @Test
    public void initializesVarianceWithPopulationWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedIndicator<Num> returns = new FixedIndicator<>(series, numOf(0), numOf(1), numOf(3));
        ForecastStateIndicator stateIndicator = new ForecastStateIndicator(returns, 3, 0.5,
                ForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertNumEquals(4d / 3d, state.mean());
        assertNumEquals(14d / 9d, state.variance());
    }

    @Test
    public void zeroDriftModeKeepsMeanButUsesZeroDrift() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ForecastStateIndicator stateIndicator = new ForecastStateIndicator(returns, 2, 0.5);

        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertNumEquals(Math.log(1.1), state.mean());
        assertNumEquals(0, state.drift());
    }

    @Test
    public void recursiveUpdateIsStableWhenLateIndexIsRequestedFirst() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 140).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ForecastStateIndicator stateIndicator = new ForecastStateIndicator(returns, 2, 0.5,
                ForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState state = stateIndicator.getValue(3);

        assertTrue(state.isStable());
        assertEquals(3, state.observationCount());
        assertTrue(state.volatility().isPositive());
    }

    @Test
    public void invalidReturnsKeepStateUnstableUntilWindowIsValid() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 0, 100, 110, 121)
                .build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ForecastStateIndicator stateIndicator = new ForecastStateIndicator(returns, 2, 0.5,
                ForecastStateIndicator.DriftMode.ROLLING_MEAN);

        assertTrue(stateIndicator.getValue(2).mean().isNaN());
        assertTrue(stateIndicator.getValue(3).mean().isNaN());
        assertTrue(stateIndicator.getValue(4).isStable());
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.averages.EWMAIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.indicators.statistics.EwmaVarianceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EwmaReturnForecastStateIndicatorTest
        extends AbstractIndicatorTest<LogReturnIndicator, ReturnForecastState> {

    public EwmaReturnForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void initializesRollingMeanStateAfterWarmup() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 133.1).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        assertSame(returns, stateIndicator.getReturnIndicator());
        assertEquals(ReturnRepresentation.LOG, stateIndicator.getReturnRepresentation());
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
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(1),
                numOf(3));
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 3, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertNumEquals(4d / 3d, state.mean());
        assertNumEquals(14d / 9d, state.variance());
    }

    @Test
    public void zeroDriftModeKeepsMeanButUsesZeroDrift() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5);

        ReturnForecastState state = stateIndicator.getValue(2);

        assertTrue(state.isStable());
        assertNumEquals(Math.log(1.1), state.mean());
        assertNumEquals(0, state.drift());
    }

    @Test
    public void recursiveUpdateIsStableWhenLateIndexIsRequestedFirst() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121, 140).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

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
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        assertTrue(stateIndicator.getValue(2).mean().isNaN());
        assertTrue(stateIndicator.getValue(3).mean().isNaN());
        ReturnForecastState recovered = stateIndicator.getValue(4);
        assertTrue(recovered.isStable());
        assertEquals(2, recovered.observationCount());
    }

    @Test
    public void rejectsNonLogReturnRepresentations() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        for (ReturnRepresentation representation : List.of(ReturnRepresentation.DECIMAL,
                ReturnRepresentation.PERCENTAGE, ReturnRepresentation.MULTIPLICATIVE)) {
            FixedReturnIndicator returns = new FixedReturnIndicator(series, representation, numOf(0), numOf(1),
                    numOf(3));

            assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateIndicator(returns, 2, 0.5));
        }
    }

    @Test
    public void rejectsInvalidDecayFactors() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);

        assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateIndicator(returns, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateIndicator(returns, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EwmaReturnForecastStateIndicator(returns, 2, Double.NaN));
    }

    @Test
    public void sharedMeanReAnchorsWithVarianceAfterPrune() {
        // The state must pair the variance with the same EWMA mean the
        // variance re-anchors on prune; a separate mean estimator would keep
        // its pre-prune recursion and publish a stale mean/drift next to the
        // re-anchored variance.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1.1051709180756477, 1.22140275816017, 1.3498588075760032, 1.4918246976412703,
                        1.6487212707001282, 1.8221188003905089, 2.0137527074704766, 5.4739473917272, 14.879731724872837,
                        40.44730436006742)
                .build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator stateIndicator = new EwmaReturnForecastStateIndicator(returns, 3, 0.9,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);

        // Warm the caches against the full prefix so stale recursions would
        // surface after the prune.
        assertTrue(stateIndicator.getValue(10).isStable());

        series.setMaximumBarCount(6);
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(42.52108155356342)
                .highPrice(42.52108155356342)
                .lowPrice(42.52108155356342)
                .closePrice(42.52108155356342)
                .build());
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(44.70118357203251)
                .highPrice(44.70118357203251)
                .lowPrice(44.70118357203251)
                .closePrice(44.70118357203251)
                .build());
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(series.getLastBar().getEndTime().plus(Duration.ofDays(1)))
                .openPrice(46.99306319730219)
                .highPrice(46.99306319730219)
                .lowPrice(46.99306319730219)
                .closePrice(46.99306319730219)
                .build());

        ReturnForecastState state = stateIndicator.getValue(13);
        assertTrue(state.isStable());

        // Fresh estimators built after the prune re-anchor from the retained
        // head; the stale full-history mean (~0.26) would fail this check.
        assertNumEquals(new EWMAIndicator(returns, 3, 0.9).getValue(13), state.mean(), 1e-9);
        assertNumEquals(new EwmaVarianceIndicator(returns, 3, 0.9).getValue(13), state.variance(), 1e-9);
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
}

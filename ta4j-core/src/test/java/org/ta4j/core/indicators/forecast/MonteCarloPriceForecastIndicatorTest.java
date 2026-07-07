/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.adapters.LogReturnToPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.indicators.forecast.projection.Forecast;

public class MonteCarloPriceForecastIndicatorTest
        extends AbstractIndicatorTest<MonteCarloPriceForecastIndicator, Forecast<Num>> {

    public MonteCarloPriceForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void horizonConstructorInfersPriceSourceFromLogReturns() {
        BarSeries series = constantSeries(300, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        MonteCarloPriceForecastIndicator forecast = new MonteCarloPriceForecastIndicator(state, 5);

        Forecast<Num> prediction = forecast.getValue(series.getEndIndex());

        assertTrue(prediction.isStable());
        assertEquals(5, prediction.horizon());
        assertNumEquals(100, prediction.median());
        assertNumEquals(100, prediction.quantile(0.05));
        assertNumEquals(100, prediction.quantile(0.95));
    }

    @Test
    public void inferredPriceForecastMatchesExplicitReducer() {
        BarSeries series = constantSeries(300, 100);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        MonteCarloPriceForecastIndicator inferred = new MonteCarloPriceForecastIndicator(state, 5);
        ReturnForecastProjectionIndicator returnProjection = new MonteCarloReturnProjectionIndicator(state, 5);
        LogReturnToPriceForecastIndicator explicit = new LogReturnToPriceForecastIndicator(close, returnProjection);

        Forecast<Num> expected = explicit.getValue(series.getEndIndex());
        Forecast<Num> actual = inferred.getValue(series.getEndIndex());

        assertEquivalent(expected, actual);
    }

    @Test
    public void rejectsCustomLogReturnStateWithoutSourceIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(0),
                numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);

        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPriceForecastIndicator(state, 1));
    }

    @Test
    public void rejectsNonLogStateIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.DECIMAL, numOf(0),
                numOf(0), numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPriceForecastIndicator(state, 1));
    }

    private BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private void assertEquivalent(Forecast<Num> expected, Forecast<Num> actual) {
        assertEquals(expected.isStable(), actual.isStable());
        assertEquals(expected.sampleCount(), actual.sampleCount());
        assertEquals(expected.horizon(), actual.horizon());
        assertNumEquals(expected.mean(), actual.mean());
        assertNumEquals(expected.median(), actual.median());
        assertNumEquals(expected.standardDeviation(), actual.standardDeviation());
        for (Double probability : List.of(0.05, 0.25, 0.5, 0.75, 0.95)) {
            assertNumEquals(expected.quantile(probability), actual.quantile(probability));
        }
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

    private static final class FixedReturnStateIndicator implements ReturnForecastStateIndicator {

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
            return new ReturnForecastState(index, index + 1, true, zero, zero, zero, zero);
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

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
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.walkforward.PredictionSnapshot;

public class MonteCarloReturnProjectionIndicatorTest
        extends AbstractIndicatorTest<LogReturnIndicator, PredictionSnapshot.Forecast<Num>> {

    public MonteCarloReturnProjectionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void horizonConstructorBuildsUsableDefaultStateForecast() {
        BarSeries series = constantSeries(300, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        MonteCarloReturnProjectionIndicator forecast = new MonteCarloReturnProjectionIndicator(state, 5);

        PredictionSnapshot.Forecast<Num> prediction = forecast.getValue(series.getEndIndex());

        assertEquals(ReturnRepresentation.LOG, forecast.getReturnRepresentation());
        assertTrue(prediction.isStable());
        assertEquals(5, prediction.horizon());
        assertNumEquals(0, prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void constantPriceProducesCollapsedReturnForecast() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100)
                .build();
        MonteCarloReturnProjectionIndicator forecast = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL, 2, 50, 3, 42L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);

        PredictionSnapshot.Forecast<Num> prediction = forecast.getValue(3);

        assertTrue(prediction.isStable());
        assertEquals(50, prediction.sampleCount());
        assertNumEquals(0, prediction.mean());
        assertNumEquals(0, prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void sameSeedAndIndexAreIndependentOfCallOrder() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108, 106, 111)
                .build();
        MonteCarloReturnProjectionIndicator first = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 2, 100, 4, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA);
        MonteCarloReturnProjectionIndicator second = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 2, 100, 4, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA);

        PredictionSnapshot.Forecast<Num> expected = first.getValue(6);
        second.getValue(7);
        PredictionSnapshot.Forecast<Num> actual = second.getValue(6);

        assertEquivalent(expected, actual);
    }

    @Test
    public void empiricalAndNormalShockModelsProduceStableOrderedQuantiles() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 103, 101, 107, 104, 110, 108, 113)
                .build();
        MonteCarloReturnProjectionIndicator empirical = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP, 1, 100, 4, 11L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);
        MonteCarloReturnProjectionIndicator normal = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 1, 100, 4, 11L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);

        PredictionSnapshot.Forecast<Num> empiricalPrediction = empirical.getValue(6);
        PredictionSnapshot.Forecast<Num> normalPrediction = normal.getValue(6);

        assertTrue(empiricalPrediction.isStable());
        assertTrue(normalPrediction.isStable());
        assertTrue(empiricalPrediction.quantile(0.05).isLessThanOrEqual(empiricalPrediction.quantile(0.95)));
        assertTrue(normalPrediction.standardDeviation().isPositive());
    }

    @Test
    public void rejectsNonLogStateProvider() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.DECIMAL, numOf(0),
                numOf(1), numOf(2));
        FixedReturnStateProvider stateProvider = new FixedReturnStateProvider(returns, ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class, () -> new MonteCarloReturnProjectionIndicator(stateProvider));
    }

    private MonteCarloReturnProjectionIndicator forecast(BarSeries series,
            MonteCarloReturnProjectionIndicator.ShockModel shockModel, int horizon, int iterations, int lookback,
            long seed, MonteCarloReturnProjectionIndicator.VolatilityUpdateMode updateMode) {
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);
        return MonteCarloReturnProjectionIndicator.builder(state)
                .horizon(horizon)
                .iterationCount(iterations)
                .lookbackBarCount(lookback)
                .seed(seed)
                .shockModel(shockModel)
                .volatilityUpdateMode(updateMode)
                .quantiles(0.05, 0.5, 0.95)
                .build();
    }

    private BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private void assertEquivalent(PredictionSnapshot.Forecast<Num> expected, PredictionSnapshot.Forecast<Num> actual) {
        assertEquals(expected.isStable(), actual.isStable());
        assertEquals(expected.sampleCount(), actual.sampleCount());
        assertNumEquals(expected.mean(), actual.mean());
        assertNumEquals(expected.median(), actual.median());
        assertNumEquals(expected.standardDeviation(), actual.standardDeviation());
        for (Double probability : List.of(0.05, 0.5, 0.95)) {
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

    private static final class FixedReturnStateProvider implements ReturnForecastStateProvider {

        private final ReturnIndicator returns;
        private final ReturnRepresentation representation;

        private FixedReturnStateProvider(ReturnIndicator returns, ReturnRepresentation representation) {
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

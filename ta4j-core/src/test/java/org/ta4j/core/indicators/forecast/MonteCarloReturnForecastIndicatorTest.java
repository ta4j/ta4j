/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.walkforward.PredictionSnapshot;

public class MonteCarloReturnForecastIndicatorTest
        extends AbstractIndicatorTest<LogReturnIndicator, PredictionSnapshot.Forecast<Num>> {

    public MonteCarloReturnForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void constantPriceProducesCollapsedReturnForecast() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100)
                .build();
        MonteCarloReturnForecastIndicator forecast = forecast(series,
                MonteCarloReturnForecastIndicator.ShockModel.STANDARDIZED_EMPIRICAL, 2, 50, 3, 42L,
                MonteCarloReturnForecastIndicator.VolatilityUpdateMode.CONSTANT);

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
        MonteCarloReturnForecastIndicator first = forecast(series, MonteCarloReturnForecastIndicator.ShockModel.NORMAL,
                2, 100, 4, 7L, MonteCarloReturnForecastIndicator.VolatilityUpdateMode.EWMA);
        MonteCarloReturnForecastIndicator second = forecast(series, MonteCarloReturnForecastIndicator.ShockModel.NORMAL,
                2, 100, 4, 7L, MonteCarloReturnForecastIndicator.VolatilityUpdateMode.EWMA);

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
        MonteCarloReturnForecastIndicator empirical = forecast(series,
                MonteCarloReturnForecastIndicator.ShockModel.HISTORICAL_BOOTSTRAP, 1, 100, 4, 11L,
                MonteCarloReturnForecastIndicator.VolatilityUpdateMode.CONSTANT);
        MonteCarloReturnForecastIndicator normal = forecast(series, MonteCarloReturnForecastIndicator.ShockModel.NORMAL,
                1, 100, 4, 11L, MonteCarloReturnForecastIndicator.VolatilityUpdateMode.CONSTANT);

        PredictionSnapshot.Forecast<Num> empiricalPrediction = empirical.getValue(6);
        PredictionSnapshot.Forecast<Num> normalPrediction = normal.getValue(6);

        assertTrue(empiricalPrediction.isStable());
        assertTrue(normalPrediction.isStable());
        assertTrue(empiricalPrediction.quantile(0.05).isLessThanOrEqual(empiricalPrediction.quantile(0.95)));
        assertTrue(normalPrediction.standardDeviation().isPositive());
    }

    private MonteCarloReturnForecastIndicator forecast(BarSeries series,
            MonteCarloReturnForecastIndicator.ShockModel shockModel, int horizon, int iterations, int lookback,
            long seed, MonteCarloReturnForecastIndicator.VolatilityUpdateMode updateMode) {
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ForecastStateIndicator state = ForecastStateIndicator.ofEwma(returns, 2, 0.5,
                ForecastStateIndicator.DriftMode.ROLLING_MEAN);
        return MonteCarloReturnForecastIndicator.builder(returns, state)
                .horizon(horizon)
                .iterationCount(iterations)
                .lookbackBarCount(lookback)
                .seed(seed)
                .shockModel(shockModel)
                .volatilityUpdateMode(updateMode)
                .quantiles(0.05, 0.5, 0.95)
                .build();
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
}

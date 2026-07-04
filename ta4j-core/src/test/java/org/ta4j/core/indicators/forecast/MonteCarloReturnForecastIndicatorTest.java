/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MonteCarloReturnForecastIndicatorTest
        extends AbstractIndicatorTest<LogReturnIndicator, ForecastDistribution<Num>> {

    public MonteCarloReturnForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void constantPriceProducesCollapsedReturnDistribution() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100)
                .build();
        MonteCarloReturnForecastIndicator forecast = forecast(series, ShockModel.STANDARDIZED_EMPIRICAL, 2, 50, 3, 42L,
                VolatilityUpdateMode.CONSTANT);

        ForecastDistribution<Num> distribution = forecast.getValue(3);

        assertTrue(distribution.defined());
        assertEquals(50, distribution.sampleCount());
        assertNumEquals(0, distribution.mean());
        assertNumEquals(0, distribution.median());
        assertNumEquals(0, distribution.standardDeviation());
    }

    @Test
    public void sameSeedAndIndexAreIndependentOfCallOrder() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108, 106, 111)
                .build();
        MonteCarloReturnForecastIndicator first = forecast(series, ShockModel.NORMAL, 2, 100, 4, 7L,
                VolatilityUpdateMode.EWMA);
        MonteCarloReturnForecastIndicator second = forecast(series, ShockModel.NORMAL, 2, 100, 4, 7L,
                VolatilityUpdateMode.EWMA);

        ForecastDistribution<Num> expected = first.getValue(6);
        second.getValue(7);
        ForecastDistribution<Num> actual = second.getValue(6);

        assertEquivalent(expected, actual);
    }

    @Test
    public void empiricalAndNormalShockModelsProduceDefinedOrderedQuantiles() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 103, 101, 107, 104, 110, 108, 113)
                .build();
        MonteCarloReturnForecastIndicator empirical = forecast(series, ShockModel.HISTORICAL_BOOTSTRAP, 1, 100, 4, 11L,
                VolatilityUpdateMode.CONSTANT);
        MonteCarloReturnForecastIndicator normal = forecast(series, ShockModel.NORMAL, 1, 100, 4, 11L,
                VolatilityUpdateMode.CONSTANT);

        ForecastDistribution<Num> empiricalDistribution = empirical.getValue(6);
        ForecastDistribution<Num> normalDistribution = normal.getValue(6);

        assertTrue(empiricalDistribution.defined());
        assertTrue(normalDistribution.defined());
        assertTrue(empiricalDistribution.quantile(0.05).isLessThanOrEqual(empiricalDistribution.quantile(0.95)));
        assertTrue(normalDistribution.standardDeviation().isPositive());
    }

    @Test
    public void validationRejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> MonteCarloForecastConfig.builder().horizon(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloForecastConfig.builder().iterationCount(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloForecastConfig.builder().lookbackBarCount(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloForecastConfig.builder().quantiles(0.5, 1.1).build());
    }

    private MonteCarloReturnForecastIndicator forecast(BarSeries series, ShockModel shockModel, int horizon,
            int iterations, int lookback, long seed, VolatilityUpdateMode updateMode) {
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateConfig stateConfig = EwmaReturnForecastStateConfig.builder()
                .initializationBarCount(2)
                .decayFactor(0.5)
                .driftMode(DriftMode.ROLLING_MEAN)
                .build();
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, stateConfig);
        MonteCarloForecastConfig forecastConfig = MonteCarloForecastConfig.builder()
                .horizon(horizon)
                .iterationCount(iterations)
                .lookbackBarCount(lookback)
                .seed(seed)
                .shockModel(shockModel)
                .volatilityUpdateMode(updateMode)
                .quantiles(0.05, 0.5, 0.95)
                .build();
        return new MonteCarloReturnForecastIndicator(returns, state, forecastConfig);
    }

    private void assertEquivalent(ForecastDistribution<Num> expected, ForecastDistribution<Num> actual) {
        assertEquals(expected.defined(), actual.defined());
        assertEquals(expected.sampleCount(), actual.sampleCount());
        assertNumEquals(expected.mean(), actual.mean());
        assertNumEquals(expected.median(), actual.median());
        assertNumEquals(expected.standardDeviation(), actual.standardDeviation());
        for (Double probability : List.of(0.05, 0.5, 0.95)) {
            assertNumEquals(expected.quantile(probability), actual.quantile(probability));
        }
    }
}

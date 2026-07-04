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
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastIndicatorsTest extends AbstractIndicatorTest<Indicator<Num>, ForecastDistribution<Num>> {

    public ForecastIndicatorsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void factoryMatchesManualPipeline() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108, 106)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EwmaReturnForecastStateConfig stateConfig = stateConfig();
        MonteCarloForecastConfig forecastConfig = forecastConfig(2, 75, 3, 123L);

        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, stateConfig);
        MonteCarloReturnForecastIndicator returnForecast = new MonteCarloReturnForecastIndicator(returns, state,
                forecastConfig);
        LogReturnToPriceForecastIndicator manual = new LogReturnToPriceForecastIndicator(close, returnForecast);
        ForecastDistributionIndicator<Num> factory = ForecastIndicators.ewmaVolatilityClosePriceForecast(close,
                stateConfig, forecastConfig);

        assertEquivalent(manual.getValue(5), factory.getValue(5));
    }

    @Test
    public void medianClosePriceForecastIsUsableAsPointIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicator<Num> median = ForecastIndicators.ewmaVolatilityMedianClosePriceForecast(close, stateConfig(),
                forecastConfig(1, 50, 3, 42L));

        assertNumEquals(100, median.getValue(3));
    }

    @Test
    public void standardPipelineDoesNotReadFuturePrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108)
                .build();
        GuardedIndicator guardedClose = new GuardedIndicator(new ClosePriceIndicator(series));
        ForecastDistributionIndicator<Num> forecast = ForecastIndicators.ewmaVolatilityClosePriceForecast(guardedClose,
                stateConfig(), forecastConfig(1, 50, 3, 42L));

        guardedClose.allowUpTo(3);
        ForecastDistribution<Num> distribution = forecast.getValue(3);

        assertTrue(distribution.defined());
    }

    private EwmaReturnForecastStateConfig stateConfig() {
        return EwmaReturnForecastStateConfig.builder()
                .initializationBarCount(2)
                .decayFactor(0.5)
                .driftMode(DriftMode.ROLLING_MEAN)
                .build();
    }

    private MonteCarloForecastConfig forecastConfig(int horizon, int iterations, int lookback, long seed) {
        return MonteCarloForecastConfig.builder()
                .horizon(horizon)
                .iterationCount(iterations)
                .lookbackBarCount(lookback)
                .seed(seed)
                .shockModel(ShockModel.STANDARDIZED_EMPIRICAL)
                .quantiles(0.05, 0.5, 0.95)
                .build();
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

    private static final class GuardedIndicator implements Indicator<Num> {

        private final Indicator<Num> delegate;
        private int maxAllowedIndex;

        private GuardedIndicator(Indicator<Num> delegate) {
            this.delegate = delegate;
        }

        private void allowUpTo(int maxAllowedIndex) {
            this.maxAllowedIndex = maxAllowedIndex;
        }

        @Override
        public Num getValue(int index) {
            if (index > maxAllowedIndex) {
                throw new AssertionError("Read future index " + index + " above " + maxAllowedIndex);
            }
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        @Override
        public BarSeries getBarSeries() {
            return delegate.getBarSeries();
        }
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.walkforward.PredictionSnapshot;

public class LogReturnToPriceForecastIndicatorTest
        extends AbstractIndicatorTest<LogReturnToPriceForecastIndicator, PredictionSnapshot.Forecast<Num>> {

    public LogReturnToPriceForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void mapsDistribution() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 100, 100).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num up = numOf(Math.log(1.1));
        Num down = numOf(Math.log(0.9));
        PredictionSnapshot.Forecast<Num> logReturnForecast = PredictionSnapshot.Forecast.ofSamples(1, 1,
                List.of(down, up), List.of(0.0, 0.5, 1.0));
        ForecastPredictionIndicator returnForecast = new FixedForecastIndicator(series, 1,
                Map.of(1, logReturnForecast));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        PredictionSnapshot.Forecast<Num> forecast = priceForecast.getValue(1);

        assertTrue(forecast.isStable());
        assertNumEquals(100 * Math.sqrt(0.99), forecast.median());
        assertNumEquals(100 * Math.sqrt(0.99), forecast.quantile(0.5));
        assertNumEquals(90d, forecast.quantiles().get(0.0));
        assertNumEquals(110d, forecast.quantiles().get(1.0));
    }

    @Test
    public void propagatesUnstableAndInvalidPrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 0).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ForecastPredictionIndicator returnForecast = new FixedForecastIndicator(series, 0,
                Map.of(1, PredictionSnapshot.Forecast.ofSamples(1, 1, List.of(numOf(0)))));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        assertTrue(priceForecast.getValue(0).mean().isNaN());
        assertTrue(priceForecast.getValue(1).mean().isNaN());
    }

    private static final class FixedForecastIndicator implements ForecastPredictionIndicator {

        private final BarSeries series;
        private final int unstableBars;
        private final Map<Integer, PredictionSnapshot.Forecast<Num>> values;

        private FixedForecastIndicator(BarSeries series, int unstableBars,
                Map<Integer, PredictionSnapshot.Forecast<Num>> values) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.values = values;
        }

        @Override
        public PredictionSnapshot.Forecast<Num> getValue(int index) {
            return values.getOrDefault(index, PredictionSnapshot.Forecast.unstable(index, 1, NaN.NaN));
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}

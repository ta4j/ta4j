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
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.walkforward.PredictionSnapshot;

public class ForecastProjectionProviderTest
        extends AbstractIndicatorTest<ForecastProjectionProvider<ReturnForecastState>, Num> {

    public ForecastProjectionProviderTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void projectionMethodsReturnPointIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(2, 1,
                List.of(numOf(1), numOf(3)), List.of(0.05, 0.5, 0.95));
        ForecastProjectionProvider<ReturnForecastState> indicator = new FixedForecastIndicator(series, 1,
                Map.of(2, forecast));

        assertNumEquals(2, indicator.mean().getValue(2));
        assertNumEquals(2, indicator.median().getValue(2));
        assertNumEquals(2.9, indicator.quantile(0.95).getValue(2));
        assertNumEquals(1, indicator.standardDeviation().getValue(2));
    }

    @Test
    public void missingQuantileProjectionReturnsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(2, 1,
                List.of(numOf(1), numOf(3)), List.of(0.5));
        ForecastProjectionProvider<ReturnForecastState> indicator = new FixedForecastIndicator(series, 0,
                Map.of(2, forecast));

        assertTrue(indicator.quantile(0.95).getValue(2).isNaN());
    }

    private static final class FixedForecastIndicator implements ForecastProjectionProvider<ReturnForecastState> {

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

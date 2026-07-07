/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import static org.junit.Assert.assertEquals;
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

public class ForwardForecastIndicatorTest extends AbstractIndicatorTest<ForwardForecastIndicator, Num> {

    public ForwardForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void reducesDistribution() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        PredictionSnapshot.Forecast<Num> forecastSummary = PredictionSnapshot.Forecast.ofSamples(2, 1,
                List.of(numOf(1), numOf(3)));
        ForecastProjectionIndicator forecast = new FixedForecastIndicator(series, 1, Map.of(2, forecastSummary));
        ForwardForecastIndicator median = new ForwardForecastIndicator(forecast, PredictionSnapshot.Forecast::median);
        ForwardForecastIndicator p95 = new ForwardForecastIndicator(forecast, value -> value.quantile(0.95));

        assertEquals(1, median.getCountOfUnstableBars());
        assertNumEquals(2, median.getValue(2));
        assertNumEquals(2.9, p95.getValue(2));
        assertTrue(median.getValue(1).isNaN());
    }

    private static final class FixedForecastIndicator implements ForecastProjectionIndicator {

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

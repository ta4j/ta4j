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

public class ForwardForecastIndicatorTest extends AbstractIndicatorTest<ForwardForecastIndicator, Num> {

    public ForwardForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void reducesDistribution() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        Forecast forecastSummary = Forecast.ofSamples(2, 1, List.of(numOf(1), numOf(3)));
        ForecastProjectionIndicator forecast = new FixedForecastIndicator(series, 1, Map.of(2, forecastSummary));
        ForwardForecastIndicator median = new ForwardForecastIndicator(forecast, Forecast::median);
        ForwardForecastIndicator p95 = new ForwardForecastIndicator(forecast, value -> value.quantile(0.95));

        assertEquals(1, median.getCountOfUnstableBars());
        assertNumEquals(2, median.getValue(2));
        assertNumEquals(2.9, p95.getValue(2));
        assertTrue(median.getValue(1).isNaN());
    }

    @Test
    public void rejectsUnavailableAndMismatchedForecastMetadata() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        Forecast wrongIndex = Forecast.ofSamples(2, 1, List.of(numOf(2)));
        Forecast wrongHorizon = Forecast.ofSamples(2, 2, List.of(numOf(3)));
        ForecastProjectionIndicator forecast = new FixedForecastIndicator(series, 0,
                Map.of(1, wrongIndex, 2, wrongHorizon));
        ForwardForecastIndicator median = new ForwardForecastIndicator(forecast, Forecast::median);

        assertTrue(median.getValue(0).isNaN());
        assertTrue(median.getValue(1).isNaN());
        assertTrue(median.getValue(2).isNaN());
    }

    private static final class FixedForecastIndicator implements ForecastProjectionIndicator {

        private final BarSeries series;
        private final int unstableBars;
        private final Map<Integer, Forecast> values;

        private FixedForecastIndicator(BarSeries series, int unstableBars, Map<Integer, Forecast> values) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.values = values;
        }

        @Override
        public Forecast getValue(int index) {
            return values.getOrDefault(index, Forecast.unstable(index, 1));
        }

        @Override
        public int getHorizon() {
            return 1;
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

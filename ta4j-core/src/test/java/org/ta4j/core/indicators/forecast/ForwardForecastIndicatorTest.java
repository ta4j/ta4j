/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

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
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(2, 1, List.of(numOf(1), numOf(3)));
        ForecastDistributionIndicator<Num> forecast = new FixedForecastIndicator(series, 1, Map.of(2, distribution));
        ForwardForecastIndicator median = new ForwardForecastIndicator(forecast, ForecastReducers.median());
        ForwardForecastIndicator p95 = new ForwardForecastIndicator(forecast, ForecastReducers.quantile(0.95));

        assertEquals(1, median.getCountOfUnstableBars());
        assertNumEquals(2, median.getValue(2));
        assertNumEquals(2.9, p95.getValue(2));
        assertTrue(median.getValue(1).isNaN());
    }

    private static final class FixedForecastIndicator implements ForecastDistributionIndicator<Num> {

        private final BarSeries series;
        private final int unstableBars;
        private final Map<Integer, ForecastDistribution<Num>> values;

        private FixedForecastIndicator(BarSeries series, int unstableBars,
                Map<Integer, ForecastDistribution<Num>> values) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.values = values;
        }

        @Override
        public ForecastDistribution<Num> getValue(int index) {
            return values.getOrDefault(index, ForecastDistribution.undefined(index, 1, NaN.NaN));
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

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastDistributionIndicatorTest extends AbstractIndicatorTest<ForecastDistributionIndicator, Num> {

    public ForecastDistributionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void exposesSummaryProjectionIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(2, 1, List.of(numOf(1), numOf(3)),
                List.of(0.5));
        ForecastDistributionIndicator forecast = new FixedForecastIndicator(series, 1, Map.of(2, distribution));

        Indicator<Num> mean = forecast.mean();
        Indicator<Num> median = forecast.median();
        Indicator<Num> standardDeviation = forecast.standardDeviation();
        Indicator<Num> p50 = forecast.quantile(0.5);
        Indicator<Num> p95 = forecast.quantile(0.95);

        assertEquals(1, median.getCountOfUnstableBars());
        assertNumEquals(2, mean.getValue(2));
        assertNumEquals(2, median.getValue(2));
        assertNumEquals(1, standardDeviation.getValue(2));
        assertNumEquals(2, p50.getValue(2));
        assertTrue(p95.getValue(2).isNaN());
        assertTrue(median.getValue(1).isNaN());
    }

    @Test
    public void rejectsInvalidQuantileProbabilities() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        ForecastDistributionIndicator forecast = new FixedForecastIndicator(series, 0, Map.of());

        assertThrows(IllegalArgumentException.class, () -> forecast.quantile(-0.1));
        assertThrows(IllegalArgumentException.class, () -> forecast.quantile(Double.NaN));
    }

    private static final class FixedForecastIndicator implements ForecastDistributionIndicator {

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
            return values.getOrDefault(index, ForecastDistribution.unstable(index, 1, NaN.NaN));
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

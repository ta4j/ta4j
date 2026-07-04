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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastCoreIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ForecastCoreIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void forwardForecastIndicatorReducesDistribution() {
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

    @Test
    public void logReturnMatchesKnownPrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator oneBar = new LogReturnIndicator(close);
        LogReturnIndicator twoBar = new LogReturnIndicator(close, 2);

        assertEquals(1, oneBar.getCountOfUnstableBars());
        assertTrue(oneBar.getValue(0).isNaN());
        assertNumEquals(Math.log(1.1), oneBar.getValue(1));
        assertNumEquals(Math.log(1.1), oneBar.getValue(2));
        assertNumEquals(Math.log(1.21), twoBar.getValue(2));
    }

    @Test
    public void logReturnRejectsInvalidAndNonPositiveInputs() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 0, -1, 110).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);

        assertTrue(returns.getValue(1).isNaN());
        assertTrue(returns.getValue(2).isNaN());
        assertTrue(returns.getValue(3).isNaN());
        assertThrows(IllegalArgumentException.class, () -> new LogReturnIndicator(new ClosePriceIndicator(series), 0));
    }

    @Test
    public void logReturnToPriceForecastMapsDistribution() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 100, 100).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num up = numOf(Math.log(1.1));
        Num down = numOf(Math.log(0.9));
        ForecastDistribution<Num> logReturnDistribution = ForecastDistribution.ofSamples(1, 1, List.of(down, up),
                List.of(0.0, 0.5, 1.0));
        ForecastDistributionIndicator<Num> returnForecast = new FixedForecastIndicator(series, 1,
                Map.of(1, logReturnDistribution));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        ForecastDistribution<Num> distribution = priceForecast.getValue(1);

        assertTrue(distribution.defined());
        assertNumEquals(100 * Math.sqrt(0.99), distribution.median());
        assertNumEquals(100 * Math.sqrt(0.99), distribution.quantile(0.5));
        assertNumEquals(90d, distribution.quantiles().get(0.0));
        assertNumEquals(110d, distribution.quantiles().get(1.0));
    }

    @Test
    public void logReturnToPriceForecastPropagatesUndefinedAndInvalidPrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 0).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ForecastDistributionIndicator<Num> returnForecast = new FixedForecastIndicator(series, 0,
                Map.of(1, ForecastDistribution.ofSamples(1, 1, List.of(numOf(0)))));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        assertTrue(priceForecast.getValue(0).mean().isNaN());
        assertTrue(priceForecast.getValue(1).mean().isNaN());
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

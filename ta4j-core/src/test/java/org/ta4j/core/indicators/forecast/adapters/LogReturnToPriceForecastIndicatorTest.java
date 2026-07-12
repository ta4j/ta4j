/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.indicators.forecast.projection.Forecast;

public class LogReturnToPriceForecastIndicatorTest
        extends AbstractIndicatorTest<LogReturnToPriceForecastIndicator, Forecast<Num>> {

    public LogReturnToPriceForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void explicitLogReturnProjectionBuildsUsablePriceForecast() {
        BarSeries series = constantSeries(300, 100);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        ReturnForecastProjectionIndicator projection = new MonteCarloReturnProjectionIndicator(state, 5);
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, projection);

        Forecast<Num> forecast = priceForecast.getValue(series.getEndIndex());

        assertTrue(forecast.isStable());
        assertNumEquals(100, forecast.median());
        assertNumEquals(100, forecast.quantile(0.05));
        assertNumEquals(100, forecast.quantile(0.95));
    }

    @Test
    public void mapsDistribution() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 100, 100).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num up = numOf(Math.log(1.1));
        Num down = numOf(Math.log(0.9));
        Forecast<Num> logReturnForecast = Forecast.ofSamples(1, 1, List.of(down, up), List.of(0.0, 0.5, 1.0));
        ReturnForecastProjectionIndicator returnForecast = new FixedForecastIndicator(series, 1,
                ReturnRepresentation.LOG, Map.of(1, logReturnForecast));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        Forecast<Num> forecast = priceForecast.getValue(1);

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
        ReturnForecastProjectionIndicator returnForecast = new FixedForecastIndicator(series, 0,
                ReturnRepresentation.LOG, Map.of(1, Forecast.ofSamples(1, 1, List.of(numOf(0)))));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        assertTrue(priceForecast.getValue(0).mean().isNaN());
        assertTrue(priceForecast.getValue(1).mean().isNaN());
    }

    @Test
    public void returnsUnstableWhenPriceTransformationOverflows() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(100, 100).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num extremeLogReturn = doubleFactory.numOf(1_000);
        Forecast<Num> logReturnForecast = Forecast.ofSummary(1, 1, 1, extremeLogReturn, extremeLogReturn,
                doubleFactory.zero(), Map.of(0.5, extremeLogReturn));
        ReturnForecastProjectionIndicator returnForecast = new FixedForecastIndicator(series, 0,
                ReturnRepresentation.LOG, Map.of(1, logReturnForecast));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        Forecast<Num> forecast = priceForecast.getValue(1);

        assertFalse(forecast.isStable());
        assertEquals(0, forecast.sampleCount());
        assertTrue(forecast.mean().isNaN());
    }

    @Test
    public void convertsSummaryValuesFromADifferentNumFactory() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(100, 100).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num logReturn = decimalFactory.numOf("0.1");
        Forecast<Num> logReturnForecast = Forecast.ofSummary(1, 1, 17, logReturn, logReturn, decimalFactory.zero(),
                Map.of(0.5, logReturn));
        ReturnForecastProjectionIndicator returnForecast = new FixedForecastIndicator(series, 0,
                ReturnRepresentation.LOG, Map.of(1, logReturnForecast));
        LogReturnToPriceForecastIndicator priceForecast = new LogReturnToPriceForecastIndicator(close, returnForecast);

        Forecast<Num> forecast = priceForecast.getValue(1);

        assertTrue(forecast.isStable());
        assertEquals(17, forecast.sampleCount());
        assertTrue(doubleFactory.produces(forecast.mean()));
    }

    @Test
    public void rejectsNonLogReturnProjection() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 100).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ReturnForecastProjectionIndicator decimalProjection = new FixedForecastIndicator(series, 0,
                ReturnRepresentation.DECIMAL, Map.of(1, Forecast.ofSamples(1, 1, List.of(numOf(0)))));

        assertThrows(IllegalArgumentException.class,
                () -> new LogReturnToPriceForecastIndicator(close, decimalProjection));
    }

    private BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private static final class FixedForecastIndicator implements ReturnForecastProjectionIndicator {

        private final BarSeries series;
        private final int unstableBars;
        private final ReturnRepresentation representation;
        private final Map<Integer, Forecast<Num>> values;

        private FixedForecastIndicator(BarSeries series, int unstableBars, ReturnRepresentation representation,
                Map<Integer, Forecast<Num>> values) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.representation = representation;
            this.values = values;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public Forecast<Num> getValue(int index) {
            return values.getOrDefault(index, Forecast.unstable(index, 1, NaN.NaN));
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

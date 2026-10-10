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

import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LognormalApproximationPriceForecastIndicatorTest
        extends AbstractIndicatorTest<LognormalApproximationPriceForecastIndicator, Forecast> {

    public LognormalApproximationPriceForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void derivesOneCoherentLognormalDistribution() {
        BarSeries series = constantSeries(2, 100);
        Forecast source = Forecast.ofSamples(1, 1, List.of(numOf(-0.2), numOf(0.4)), List.of(0.05, 0.5, 0.95));
        FixedForecastIndicator projection = new FixedForecastIndicator(series, ReturnRepresentation.LOG,
                Map.of(1, source));
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series), projection);

        Forecast forecast = indicator.getValue(1);
        double mu = source.mean().doubleValue();
        double sigma = source.standardDeviation().doubleValue();
        double expectedMean = 100 * Math.exp(mu + sigma * sigma / 2d);
        double expectedStandardDeviation = expectedMean * Math.sqrt(Math.expm1(sigma * sigma));
        double z95 = new NormalDistribution().inverseCumulativeProbability(0.95);

        assertEquals(ForecastSupport.analytic(LognormalApproximationPriceForecastIndicator.SUPPORT_ASSUMPTION),
                forecast.support());
        assertNumEquals(expectedMean, forecast.mean());
        assertNumEquals(100 * Math.exp(mu), forecast.median());
        assertNumEquals(expectedStandardDeviation, forecast.standardDeviation());
        assertNumEquals(100 * Math.exp(mu + sigma * z95), forecast.quantile(0.95));
    }

    @Test
    public void tinyNonzeroDispersionDoesNotCollapseToZero() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(100, 100).build();
        Forecast source = summary(doubleFactory, 1, doubleFactory.zero(), doubleFactory.numOf("1E-9"), Map.of());
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series),
                new FixedForecastIndicator(series, ReturnRepresentation.LOG, Map.of(1, source)));

        Forecast forecast = indicator.getValue(1);

        assertTrue(forecast.standardDeviation().isPositive());
        assertNumEquals(1e-7, forecast.standardDeviation());
    }

    @Test
    public void dispersionRemainsRepresentableWhenLogVarianceUnderflows() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(100, 100).build();
        Forecast source = summary(doubleFactory, 1, doubleFactory.zero(), doubleFactory.numOf(1e-200), Map.of());
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series),
                new FixedForecastIndicator(series, ReturnRepresentation.LOG, Map.of(1, source)));

        Forecast forecast = indicator.getValue(1);

        assertTrue(forecast.isStable());
        assertNumEquals(1e-198, forecast.standardDeviation());
    }

    @Test
    public void centralPriceUnderflowMakesApproximationUnavailable() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory).withData(1e-300, 1e-300).build();
        Forecast source = summary(doubleFactory, 1, doubleFactory.numOf(-100), doubleFactory.zero(), Map.of());
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series),
                new FixedForecastIndicator(series, ReturnRepresentation.LOG, Map.of(1, source)));

        assertFalse(indicator.getValue(1).isStable());
    }

    @Test
    public void removedIndexRetainsRequestedMetadata() {
        BarSeries series = constantSeries(6, 100);
        Forecast source = summary(numFactory, 5, numOf(0), numOf(0), Map.of());
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series),
                new FixedForecastIndicator(series, ReturnRepresentation.LOG, Map.of(5, source)));
        series.setMaximumBarCount(3);

        Forecast removed = indicator.getValue(1);

        assertEquals(1, removed.decisionIndex());
        assertEquals(1, removed.horizon());
        assertFalse(removed.isStable());
    }

    @Test
    public void exponentGuardFailsPromptlyAndLaterIndexRecovers() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance(40);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(decimalFactory).withData(100, 100, 100).build();
        Forecast extreme = summary(decimalFactory, 1, decimalFactory.numOf(1_000), decimalFactory.zero(), Map.of());
        Forecast valid = summary(decimalFactory, 2, decimalFactory.zero(), decimalFactory.zero(), Map.of());
        FixedForecastIndicator projection = new FixedForecastIndicator(series, ReturnRepresentation.LOG,
                Map.of(1, extreme, 2, valid));
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series), projection);

        assertFalse(indicator.getValue(1).isStable());
        assertTrue(indicator.getValue(2).isStable());
        assertNumEquals(100, indicator.getValue(2).mean());
    }

    @Test
    public void unavailableEndpointTailIsOmittedWithoutLosingCentralFields() {
        BarSeries series = constantSeries(2, 100);
        Forecast source = summary(numFactory, 1, numOf(0), numOf(1), Map.of(0.5, numOf(0), 1.0, numOf(10)));
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series),
                new FixedForecastIndicator(series, ReturnRepresentation.LOG, Map.of(1, source)));

        Forecast forecast = indicator.getValue(1);

        assertTrue(forecast.isStable());
        assertTrue(forecast.hasQuantile(0.5));
        assertFalse(forecast.hasQuantile(1.0));
    }

    @Test
    public void normalizesDifferentDecimalPrecisionToPriceFactory() {
        NumFactory priceFactory = DecimalNumFactory.getInstance(3);
        NumFactory sourceFactory = DecimalNumFactory.getInstance(40);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(priceFactory).withData(100, 100).build();
        Forecast source = summary(sourceFactory, 1, sourceFactory.numOf("0.1"), sourceFactory.zero(), Map.of());
        LognormalApproximationPriceForecastIndicator indicator = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series),
                new FixedForecastIndicator(series, ReturnRepresentation.LOG, Map.of(1, source)));

        Forecast forecast = indicator.getValue(1);

        assertTrue(forecast.isStable());
        assertTrue(forecast.mean() instanceof DecimalNum);
        assertEquals(3, ((DecimalNum) forecast.mean()).getMathContext().getPrecision());
        assertEquals(0, forecast.sampleCount());
    }

    @Test
    public void rejectsMismatchedMetadataAndNonLogProjection() {
        BarSeries series = constantSeries(2, 100);
        Forecast stale = summary(numFactory, 0, numOf(0), numOf(0), Map.of());
        FixedForecastIndicator staleProjection = new FixedForecastIndicator(series, ReturnRepresentation.LOG,
                Map.of(1, stale));
        LognormalApproximationPriceForecastIndicator staleAdapter = new LognormalApproximationPriceForecastIndicator(
                new ClosePriceIndicator(series), staleProjection);

        assertFalse(staleAdapter.getValue(1).isStable());
        FixedForecastIndicator decimalProjection = new FixedForecastIndicator(series, ReturnRepresentation.DECIMAL,
                Map.of(1, summary(numFactory, 1, numOf(0), numOf(0), Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new LognormalApproximationPriceForecastIndicator(new ClosePriceIndicator(series),
                        decimalProjection));
    }

    private Forecast summary(NumFactory factory, int index, Num mean, Num standardDeviation,
            Map<Double, Num> quantiles) {
        return Forecast.builder(index, 1, factory, ForecastSupport.analytic("normal-log-return"))
                .mean(mean)
                .median(mean)
                .standardDeviation(standardDeviation)
                .quantiles(quantiles)
                .build();
    }

    private BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private static final class FixedForecastIndicator implements ReturnForecastProjectionIndicator {

        private final BarSeries series;
        private final ReturnRepresentation representation;
        private final Map<Integer, Forecast> values;

        private FixedForecastIndicator(BarSeries series, ReturnRepresentation representation,
                Map<Integer, Forecast> values) {
            this.series = series;
            this.representation = representation;
            this.values = values;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
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
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}

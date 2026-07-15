/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Map;
import java.util.function.IntFunction;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RollingConformalForecastProjectionIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Forecast> {

    public RollingConformalForecastProjectionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void finiteSampleScoreWidensTailsAndPreservesCentralFieldsAndSupport() {
        BarSeries series = series(7);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.analytic("test-model"), 10, 2));
        FixedIndicator<Num> realized = values(series, 0, 11, 12, 13, 14, 15, 16);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(5)
                .minimumCalibrationCount(5)
                .build();

        Forecast forecast = calibrated.getValue(5);

        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.analytic("test-model"), forecast.support());
        assertNumEquals(10, forecast.mean());
        assertNumEquals(10, forecast.median());
        assertNumEquals(2, forecast.standardDeviation());
        assertNumEquals(2, forecast.quantile(0.05));
        assertNumEquals(18, forecast.quantile(0.95));
    }

    @Test
    public void targetCoverageWaitsForAnAchievableFiniteSampleRank() {
        BarSeries series = series(10);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        FixedIndicator<Num> realized = values(series, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.9)
                .calibrationWindow(10)
                .minimumCalibrationCount(5)
                .build();

        assertEquals(9, calibrated.getCountOfUnstableBars());
        assertFalse(calibrated.getValue(8).isStable());
        assertTrue(calibrated.getValue(9).isStable());
    }

    @Test
    public void declaredBaseWarmupPreventsPrematureStableOutput() {
        BarSeries series = series(7);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1, 3,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        FixedIndicator<Num> realized = values(series, 0, 1, 1, 1, 1, 1, 1);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(2)
                .minimumCalibrationCount(2)
                .build();

        assertEquals(5, calibrated.getCountOfUnstableBars());
        assertFalse(calibrated.getValue(4).isStable());
        assertTrue(calibrated.getValue(5).isStable());
    }

    @Test
    public void onlyMaturedDecisionsCanEnterCalibration() {
        BarSeries series = series(8);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 2,
                index -> summary(index, 2, ForecastSupport.empirical(9), 0, 1));
        FixedIndicator<Num> realized = values(series, 0, 0, 1, 2, 3, 1000, 2000, 3000);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        Forecast forecast = calibrated.getValue(5);

        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.empirical(9), forecast.support());
        assertNumEquals(-8, forecast.quantile(0.05));
        assertNumEquals(8, forecast.quantile(0.95));
    }

    @Test
    public void forecastIsInvariantToUnmaturedFutureSuffix() {
        BarSeries prefixSeries = series(6);
        BarSeries extendedSeries = series(8);
        FixedForecastIndicator prefixBase = new FixedForecastIndicator(prefixSeries, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        FixedForecastIndicator extendedBase = new FixedForecastIndicator(extendedSeries, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        FixedIndicator<Num> prefixRealized = values(prefixSeries, 0, 1, 2, 3, 4, 5);
        FixedIndicator<Num> extendedRealized = values(extendedSeries, 0, 1, 2, 3, 4, 5, 500, 1000);
        RollingConformalForecastProjectionIndicator prefix = RollingConformalForecastProjectionIndicator
                .builder(prefixBase, prefixRealized)
                .targetCoverage(0.5)
                .calibrationWindow(4)
                .minimumCalibrationCount(4)
                .build();
        RollingConformalForecastProjectionIndicator extended = RollingConformalForecastProjectionIndicator
                .builder(extendedBase, extendedRealized)
                .targetCoverage(0.5)
                .calibrationWindow(4)
                .minimumCalibrationCount(4)
                .build();

        Forecast expected = prefix.getValue(5);
        Forecast actual = extended.getValue(5);

        assertEquals(expected.support(), actual.support());
        assertEquals(expected.quantiles(), actual.quantiles());
    }

    @Test
    public void cumulativeLogReturnBuilderUsesWholeHorizonRealization() {
        BarSeries series = series(7);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 2,
                index -> summary(index, 2, ForecastSupport.analytic("return-model"), 0, 1));
        FixedReturnIndicator returns = new FixedReturnIndicator(series, new double[] { 0, 1, 2, 3, 4, 5, 6 });
        ReturnForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .cumulativeLogReturnBuilder(base, returns)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        Forecast forecast = calibrated.getValue(5);

        assertTrue(forecast.isStable());
        assertEquals(ReturnRepresentation.LOG, calibrated.getReturnRepresentation());
        assertNumEquals(-12, forecast.quantile(0.05));
        assertNumEquals(12, forecast.quantile(0.95));
        assertNumEquals(-12, calibrated.quantile(0.05).getValue(5));
    }

    @Test
    public void tailLessBaseRemainsUnavailable() {
        BarSeries series = series(5);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1, Map.of()));
        FixedIndicator<Num> realized = values(series, 0, 1, 1, 1, 1);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        assertFalse(calibrated.getValue(4).isStable());
    }

    @Test
    public void medianOnlyBaseRemainsUnavailable() {
        BarSeries series = series(5);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1, Map.of(0.5, numFactory.zero())));
        FixedIndicator<Num> realized = values(series, 0, 1, 1, 1, 1);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        assertFalse(calibrated.getValue(4).isStable());
    }

    @Test
    public void oneSidedTailCanBeCalibrated() {
        BarSeries series = series(5);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1, Map.of(0.05, numFactory.numOf(-5))));
        FixedIndicator<Num> realized = values(series, 0, 1, 1, 1, 1);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        Forecast forecast = calibrated.getValue(4);

        assertTrue(forecast.isStable());
        assertNumEquals(-6, forecast.quantile(0.05));
    }

    @Test
    public void unstableOrInvalidBaseMetadataPropagatesUnavailable() {
        BarSeries series = series(6);
        FixedForecastIndicator unstableBase = new FixedForecastIndicator(series, 1,
                index -> Forecast.unstable(index, 1));
        FixedForecastIndicator wrongIndexBase = new FixedForecastIndicator(series, 1,
                index -> summary(index == 5 ? 4 : index, 1, ForecastSupport.empirical(2), 0, 1));
        FixedIndicator<Num> realized = values(series, 0, 1, 2, 3, 4, 5);
        RollingConformalForecastProjectionIndicator unstable = RollingConformalForecastProjectionIndicator
                .builder(unstableBase, realized)
                .targetCoverage(0.5)
                .calibrationWindow(2)
                .minimumCalibrationCount(2)
                .build();
        RollingConformalForecastProjectionIndicator invalid = RollingConformalForecastProjectionIndicator
                .builder(wrongIndexBase, realized)
                .targetCoverage(0.5)
                .calibrationWindow(2)
                .minimumCalibrationCount(2)
                .build();

        assertFalse(unstable.getValue(5).isStable());
        assertFalse(invalid.getValue(5).isStable());
    }

    @Test
    public void positiveWideningCannotManufactureDispersionForCollapsedBase() {
        BarSeries series = series(5);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(4), 0, 0));
        FixedIndicator<Num> realized = values(series, 0, 1, 1, 1, 1);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        assertFalse(calibrated.getValue(4).isStable());
    }

    @Test
    public void zeroAdjustmentPreservesCollapsedBase() {
        BarSeries series = series(5);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(4), 0, 0));
        FixedIndicator<Num> realized = values(series, 0, 0, 0, 0, 0);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        Forecast forecast = calibrated.getValue(4);

        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.empirical(4), forecast.support());
        assertNumEquals(0, forecast.standardDeviation());
    }

    @Test
    public void insufficientValidCalibrationRowsRemainUnavailable() {
        BarSeries series = series(6);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> index == 2 ? Forecast.unstable(index, 1)
                        : summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        FixedIndicator<Num> realized = values(series, 0, 1, 2, 3, 4, 5);
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        assertFalse(calibrated.getValue(4).isStable());
    }

    @Test
    public void defaultsRequireThirtyMaturedCalibrationScores() {
        BarSeries series = series(32);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        double[] realizedValues = new double[32];
        java.util.Arrays.fill(realizedValues, 1d);
        FixedIndicator<Num> realized = values(series, realizedValues);
        RollingConformalForecastProjectionIndicator calibrated = new RollingConformalForecastProjectionIndicator(base,
                realized);

        assertEquals(30, calibrated.getCountOfUnstableBars());
        assertFalse(calibrated.getValue(29).isStable());
        assertTrue(calibrated.getValue(30).isStable());
    }

    @Test
    public void invalidRealizationLeavesAndThenRecoversFromRollingWindow() {
        BarSeries series = series(7);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(3), 0, 1));
        FixedIndicator<Num> realized = new FixedIndicator<>(series, numOf(0), numOf(1), NaN.NaN, numOf(1), numOf(1),
                numOf(1), numOf(1));
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, realized)
                .targetCoverage(0.5)
                .calibrationWindow(3)
                .minimumCalibrationCount(3)
                .build();

        assertFalse(calibrated.getValue(4).isStable());
        assertTrue(calibrated.getValue(5).isStable());
    }

    @Test
    public void buildersRejectInvalidCoverageRepresentationAndSeries() {
        BarSeries series = series(4);
        FixedForecastIndicator base = new FixedForecastIndicator(series, 1,
                index -> summary(index, 1, ForecastSupport.empirical(2), 0, 1));
        FixedIndicator<Num> realized = values(series, 0, 1, 2, 3);
        BarSeries otherSeries = series(4);
        FixedIndicator<Num> otherRealized = values(otherSeries, 0, 1, 2, 3);
        FixedReturnIndicator decimalReturns = new FixedReturnIndicator(series, new double[] { 0, 1, 2, 3 },
                ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class,
                () -> RollingConformalForecastProjectionIndicator.builder(base, realized).targetCoverage(1d).build());
        assertThrows(IllegalArgumentException.class,
                () -> RollingConformalForecastProjectionIndicator.builder(base, realized)
                        .targetCoverage(0.9)
                        .calibrationWindow(5)
                        .minimumCalibrationCount(5)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> RollingConformalForecastProjectionIndicator.builder(base, otherRealized));
        assertThrows(IllegalArgumentException.class,
                () -> RollingConformalForecastProjectionIndicator.cumulativeLogReturnBuilder(base, decimalReturns));
    }

    private BarSeries series(int size) {
        double[] prices = new double[size];
        java.util.Arrays.fill(prices, 100d);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
    }

    private FixedIndicator<Num> values(BarSeries series, double... values) {
        Num[] numbers = java.util.Arrays.stream(values).mapToObj(series.numFactory()::numOf).toArray(Num[]::new);
        return new FixedIndicator<>(series, numbers);
    }

    private Forecast summary(int index, int horizon, ForecastSupport support, double median, double deviation) {
        NumFactory factory = numFactory;
        Num center = factory.numOf(median);
        Map<Double, Num> quantiles = deviation == 0d ? Map.of(0.05, center, 0.5, center, 0.95, center)
                : Map.of(0.05, center.minus(factory.numOf(5)), 0.5, center, 0.95, center.plus(factory.numOf(5)));
        return summary(index, horizon, support, median, deviation, quantiles);
    }

    private Forecast summary(int index, int horizon, ForecastSupport support, double median, double deviation,
            Map<Double, Num> quantiles) {
        NumFactory factory = numFactory;
        Num center = factory.numOf(median);
        Num spread = factory.numOf(deviation);
        return Forecast.builder(index, horizon, factory, support)
                .mean(center)
                .median(center)
                .standardDeviation(spread)
                .quantiles(quantiles)
                .build();
    }

    private static final class FixedForecastIndicator extends AbstractIndicator<Forecast>
            implements ReturnForecastProjectionIndicator {

        private final int horizon;
        private final int unstableBars;
        private final IntFunction<Forecast> forecasts;

        private FixedForecastIndicator(BarSeries series, int horizon, IntFunction<Forecast> forecasts) {
            this(series, horizon, 0, forecasts);
        }

        private FixedForecastIndicator(BarSeries series, int horizon, int unstableBars,
                IntFunction<Forecast> forecasts) {
            super(series);
            this.horizon = horizon;
            this.unstableBars = unstableBars;
            this.forecasts = forecasts;
        }

        @Override
        public Forecast getValue(int index) {
            return forecasts.apply(index);
        }

        @Override
        public int getHorizon() {
            return horizon;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return ReturnRepresentation.LOG;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    private static final class FixedReturnIndicator extends AbstractIndicator<Num> implements ReturnIndicator {

        private final double[] values;
        private final ReturnRepresentation representation;

        private FixedReturnIndicator(BarSeries series, double[] values) {
            this(series, values, ReturnRepresentation.LOG);
        }

        private FixedReturnIndicator(BarSeries series, double[] values, ReturnRepresentation representation) {
            super(series);
            this.values = values.clone();
            this.representation = representation;
        }

        @Override
        public Num getValue(int index) {
            return getBarSeries().numFactory().numOf(values[index]);
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}

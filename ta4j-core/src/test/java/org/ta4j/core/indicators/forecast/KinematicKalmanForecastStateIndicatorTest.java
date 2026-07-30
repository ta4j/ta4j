/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.KalmanNoiseIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class KinematicKalmanForecastStateIndicatorTest
        extends AbstractIndicatorTest<Indicator<Num>, KinematicKalmanForecastState> {

    private static final double PROCESS_NOISE = 0.01;
    private static final double MEASUREMENT_NOISE = 0.2;

    public KinematicKalmanForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesApacheCommonsMathReferenceFilter() {
        double[] observations = { 10, 10.8, 12.2, 13.1, 15.4, 16.2 };
        BarSeries series = series(observations);
        KinematicKalmanForecastStateIndicator actual = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series), PROCESS_NOISE, MEASUREMENT_NOISE);
        KalmanFilter reference = referenceFilter(observations[0]);

        reference.correct(new double[] { observations[0] });
        assertStateEquals(reference, actual.getValue(0), 1);
        for (int index = 1; index < observations.length; index++) {
            reference.predict();
            reference.correct(new double[] { observations[index] });
            assertStateEquals(reference, actual.getValue(index), index + 1);
        }
    }

    @Test
    public void dynamicNoiseUsesDecisionIndexValues() {
        BarSeries series = series(10, 20, 30);
        Indicator<Num> source = new ClosePriceIndicator(series);
        FixedIndicator<Num> processNoise = values(series, PROCESS_NOISE, 100, PROCESS_NOISE);
        FixedIndicator<Num> measurementNoise = values(series, MEASUREMENT_NOISE, MEASUREMENT_NOISE, MEASUREMENT_NOISE);
        KinematicKalmanForecastStateIndicator dynamic = new KinematicKalmanForecastStateIndicator(source,
                new KalmanNoiseIndicator(processNoise), new KalmanNoiseIndicator(measurementNoise));
        KinematicKalmanForecastStateIndicator baseline = new KinematicKalmanForecastStateIndicator(source,
                PROCESS_NOISE, MEASUREMENT_NOISE);

        assertTrue(dynamic.getValue(1)
                .position()
                .minus(numOf(20))
                .abs()
                .isLessThan(baseline.getValue(1).position().minus(numOf(20)).abs()));
        assertEquals(numOf(100), dynamic.getValue(1).processNoise());
    }

    @Test
    public void invalidInputIsUnavailableAndLaterStateRecovers() {
        BarSeries series = series(10, 20, 30);
        Indicator<Num> source = new ClosePriceIndicator(series);
        FixedIndicator<Num> processNoise = values(series, PROCESS_NOISE, 0, PROCESS_NOISE);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(source,
                new KalmanNoiseIndicator(processNoise), KalmanNoiseIndicator.constant(series, MEASUREMENT_NOISE));

        KinematicKalmanForecastState unavailable = state.getValue(1);
        KinematicKalmanForecastState recovered = state.getValue(2);

        assertFalse(unavailable.isStable());
        assertEquals(1, unavailable.observationCount());
        assertTrue(recovered.isStable());
        assertEquals(2, recovered.observationCount());

        BarSeries comparisonSeries = series(10, 30);
        KinematicKalmanForecastState expected = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(comparisonSeries), PROCESS_NOISE, MEASUREMENT_NOISE).getValue(1);
        assertEquals(expected.position().doubleValue(), recovered.position().doubleValue(), 1e-12);
        assertEquals(expected.velocity().doubleValue(), recovered.velocity().doubleValue(), 1e-12);
    }

    @Test
    public void primitiveOverflowIsUnavailableAndReinitializesOnTheNextUsableBar() {
        BarSeries series = series(10, 20, 30, 40);
        Indicator<Num> source = new ClosePriceIndicator(series);
        FixedIndicator<Num> processNoise = values(series, Double.MAX_VALUE, PROCESS_NOISE, PROCESS_NOISE,
                PROCESS_NOISE);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(source,
                new KalmanNoiseIndicator(processNoise), KalmanNoiseIndicator.constant(series, MEASUREMENT_NOISE));

        KinematicKalmanForecastState firstUpdate = state.getValue(1);
        KinematicKalmanForecastState secondUpdate = state.getValue(2);
        KinematicKalmanForecastState recovered = state.getValue(3);

        if (numFactory == DoubleNumFactory.getInstance()) {
            assertFalse(firstUpdate.isStable());
            assertTrue(secondUpdate.isStable());
            assertTrue(recovered.isStable());
            assertEquals(2, recovered.observationCount());
        } else {
            assertTrue(firstUpdate.isStable());
            assertTrue(secondUpdate.isStable());
            assertTrue(recovered.isStable());
            assertEquals(4, recovered.observationCount());
        }
    }

    @Test
    public void roundingInducedNonPositiveSemidefiniteCovarianceIsUnavailableAndRecovers() {
        BarSeries series = series(10, 20, 30, 40, 50, 60);
        FixedIndicator<Num> processNoise = values(series, 1.2323044056034407e-14, 7103.474293621034, 257698.81868602187,
                1257589254.8754091, 7.570601618500834e-11, PROCESS_NOISE);
        FixedIndicator<Num> measurementNoise = values(series, 860996637233.9683, 0.0010219796485463884,
                0.00002937051715394565, 2.5421986816576946e-15, 0.11803949640282463, MEASUREMENT_NOISE);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series), new KalmanNoiseIndicator(processNoise),
                new KalmanNoiseIndicator(measurementNoise));

        KinematicKalmanForecastState roundedState = state.getValue(4);
        KinematicKalmanForecastState recovered = state.getValue(5);

        assertFalse(roundedState.isStable());
        assertEquals(5, roundedState.observationCount());
        assertTrue(recovered.isStable());
        assertEquals(1, recovered.observationCount());
    }

    @Test
    public void reverseReadsReuseCachedStateHistory() {
        int barCount = 128;
        double[] prices = new double[barCount];
        for (int index = 0; index < prices.length; index++) {
            prices[index] = 100 + index;
        }
        BarSeries series = series(prices);
        CountingIndicator source = new CountingIndicator(new ClosePriceIndicator(series));
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(source);

        state.getValue(barCount - 1);
        for (int index = barCount - 2; index >= 0; index--) {
            state.getValue(index);
        }

        assertTrue("Reverse reads should reuse cached state", source.readCount() <= barCount + 2L);
    }

    @Test
    public void removedHistoryIsUnavailableAndRetainedHistoryStartsAtTheNewBeginIndex() {
        double[] prices = new double[15];
        for (int index = 0; index < prices.length; index++) {
            prices[index] = 100 + index;
        }
        BarSeries series = series(prices);
        series.setMaximumBarCount(3);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series));

        KinematicKalmanForecastState removed = state.getValue(1);
        KinematicKalmanForecastState retained = state.getValue(series.getEndIndex());

        assertFalse(removed.isStable());
        assertEquals(1, removed.index());
        assertEquals(0, removed.observationCount());
        assertTrue(retained.isStable());
        assertEquals(3, retained.observationCount());
    }

    @Test
    public void terminalBarReplacementRecalculatesStateAndForecast() {
        BarSeries series = series(10, 11, 12);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series));
        double originalPosition = state.getValue(2).position().doubleValue();

        Bar lastBar = series.getLastBar();
        Bar replacement = series.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime())
                .openPrice(30)
                .highPrice(30)
                .lowPrice(30)
                .closePrice(30)
                .build();
        series.addBar(replacement, true);

        assertTrue(state.getValue(2).position().doubleValue() > originalPosition);
        assertEquals(3, state.getValue(2).observationCount());
    }

    @Test
    public void concurrentRandomReadsMatchSequentialState() throws Exception {
        double[] prices = new double[64];
        for (int index = 0; index < prices.length; index++) {
            prices[index] = 100 + Math.sin(index * 0.3) + index * 0.1;
        }
        BarSeries series = series(prices);
        Indicator<Num> source = new ClosePriceIndicator(series);
        KinematicKalmanForecastStateIndicator expected = new KinematicKalmanForecastStateIndicator(source,
                PROCESS_NOISE, MEASUREMENT_NOISE);
        KinematicKalmanForecastStateIndicator concurrent = new KinematicKalmanForecastStateIndicator(source,
                PROCESS_NOISE, MEASUREMENT_NOISE);
        List<KinematicKalmanForecastState> expectedStates = new ArrayList<>(prices.length);
        for (int index = 0; index < prices.length; index++) {
            expectedStates.add(expected.getValue(index));
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<KinematicKalmanForecastState>> reads = new ArrayList<>();
            for (int task = 0; task < 256; task++) {
                int index = Math.floorMod(task * 37, prices.length);
                reads.add(executor.submit(() -> concurrent.getValue(index)));
            }
            for (int task = 0; task < reads.size(); task++) {
                int index = Math.floorMod(task * 37, prices.length);
                assertEquals(expectedStates.get(index), reads.get(task).get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectsInvalidConfigurationAndDifferentSeries() {
        BarSeries series = series(1, 2, 3);
        Indicator<Num> source = new ClosePriceIndicator(series);

        assertThrows(IllegalArgumentException.class,
                () -> new KinematicKalmanForecastStateIndicator(source, 0, MEASUREMENT_NOISE));
        assertThrows(IllegalArgumentException.class,
                () -> new KinematicKalmanForecastStateIndicator(source, PROCESS_NOISE, Double.NaN));

        BarSeries otherSeries = series(1, 2, 3);
        assertThrows(IllegalArgumentException.class,
                () -> new KinematicKalmanForecastStateIndicator(source,
                        KalmanNoiseIndicator.constant(otherSeries, PROCESS_NOISE),
                        KalmanNoiseIndicator.constant(series, MEASUREMENT_NOISE)));
    }

    @Test
    public void descriptorAndJsonRoundTrip() {
        BarSeries series = series(10, 11, 12, 13);
        KinematicKalmanForecastStateIndicator original = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series), PROCESS_NOISE, MEASUREMENT_NOISE);

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, original.toDescriptor());
        Indicator<?> jsonCopy = Indicator.fromJson(series, original.toJson());

        assertEquals(original.toDescriptor(), descriptorCopy.toDescriptor());
        assertEquals(original.toDescriptor(), jsonCopy.toDescriptor());
        assertEquals(original.getValue(3), descriptorCopy.getValue(3));
        assertEquals(original.getValue(3), jsonCopy.getValue(3));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = series(10, 11, 12);
        return List.of(serializationFixture(series, new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series), PROCESS_NOISE, MEASUREMENT_NOISE)));
    }

    private BarSeries series(double... values) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private FixedIndicator<Num> values(BarSeries series, double... values) {
        Num[] numbers = java.util.Arrays.stream(values).mapToObj(numFactory::numOf).toArray(Num[]::new);
        return new FixedIndicator<>(series, numbers);
    }

    private static KalmanFilter referenceFilter(double firstObservation) {
        RealMatrix transition = new Array2DRowRealMatrix(new double[][] { { 1, 1 }, { 0, 1 } }, false);
        RealMatrix processNoise = new Array2DRowRealMatrix(
                new double[][] { { PROCESS_NOISE, 0 }, { 0, PROCESS_NOISE } }, false);
        RealMatrix measurement = new Array2DRowRealMatrix(new double[][] { { 1, 0 } }, false);
        RealMatrix measurementNoise = new Array2DRowRealMatrix(new double[][] { { MEASUREMENT_NOISE } }, false);
        RealMatrix initialCovariance = new Array2DRowRealMatrix(
                new double[][] { { 1 + PROCESS_NOISE, 0 }, { 0, 1 + PROCESS_NOISE } }, false);
        DefaultProcessModel processModel = new DefaultProcessModel(transition, null, processNoise,
                new ArrayRealVector(new double[] { firstObservation }, false).append(0), initialCovariance);
        return new KalmanFilter(processModel, new DefaultMeasurementModel(measurement, measurementNoise));
    }

    private static void assertStateEquals(KalmanFilter reference, KinematicKalmanForecastState actual,
            int observationCount) {
        double[] expectedState = reference.getStateEstimation();
        double[][] expectedCovariance = reference.getErrorCovariance();
        assertTrue(actual.isStable());
        assertEquals(observationCount, actual.observationCount());
        assertEquals(expectedState[0], actual.position().doubleValue(), 1e-10);
        assertEquals(expectedState[1], actual.velocity().doubleValue(), 1e-10);
        assertEquals(expectedCovariance[0][0], actual.positionVariance().doubleValue(), 1e-10);
        assertEquals(expectedCovariance[0][1], actual.positionVelocityCovariance().doubleValue(), 1e-10);
        assertEquals(expectedCovariance[1][1], actual.velocityVariance().doubleValue(), 1e-10);
    }

    private static final class CountingIndicator extends AbstractIndicator<Num> {

        private final Indicator<Num> delegate;
        private final AtomicInteger readCount = new AtomicInteger();

        private CountingIndicator(Indicator<Num> delegate) {
            super(delegate.getBarSeries());
            this.delegate = delegate;
        }

        @Override
        public Num getValue(int index) {
            readCount.incrementAndGet();
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        private int readCount() {
            return readCount.get();
        }
    }
}

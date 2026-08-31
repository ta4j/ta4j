/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Assert;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;

import java.util.Arrays;

/**
 * Tests for the {@link CorrentropyKalmanWeightIndicator} view of the
 * {@link CorrentropyKalmanFilterIndicator}. The reference values are produced
 * by the same oracle that backs {@link CorrentropyKalmanFilterIndicatorTest}
 * and live in
 * {@code src/test/resources/oracles/cf-558-mckf-reference-oracle.py}.
 */
public class CorrentropyKalmanWeightIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CorrentropyKalmanWeightIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries seriesOf(double... data) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    private FixedIndicator<Num> constant(BarSeries series, double value) {
        Num[] values = new Num[series.getBarCount()];
        Arrays.fill(values, numOf(value));
        return new FixedIndicator<>(series, values);
    }

    private FixedIndicator<Num> fixed(BarSeries series, double... values) {
        Num[] nums = new Num[values.length];
        for (int i = 0; i < values.length; i++) {
            nums[i] = numOf(values[i]);
        }
        return new FixedIndicator<>(series, nums);
    }

    private CorrentropyKalmanWeightIndicator weight(Indicator<Num> source, double q, double r, double sigma) {
        BarSeries series = source.getBarSeries();
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(source, constant(series, q),
                constant(series, r), numOf(sigma));
        return filter.measurementWeight();
    }

    private void assertInUnitInterval(CorrentropyKalmanWeightIndicator weight, int fromIndex, int toIndex) {
        for (int i = fromIndex; i <= toIndex; i++) {
            double value = weight.getValue(i).doubleValue();
            Assert.assertTrue("weight at index " + i + " must be in [0, 1] but was " + value,
                    value >= 0.0 && value <= 1.0);
        }
    }

    @Test
    public void weightIsOneAtFirstValidObservationAndDropsWithDisagreement() {
        // step_response reference: the first valid observation carries unit
        // weight and later weights describe how much the measurement
        // contributed to the robust estimate.
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        CorrentropyKalmanWeightIndicator weight = weight(new ClosePriceIndicator(series), 1e-3, 1e-2, 2);

        Assert.assertEquals(1.0, weight.getValue(0).doubleValue(), 0.0);
        Assert.assertEquals(0.893288290866, weight.getValue(1).doubleValue(), 1e-9);
        Assert.assertEquals(0.999881827845, weight.getValue(2).doubleValue(), 1e-9);
        Assert.assertEquals(0.784452745524, weight.getValue(3).doubleValue(), 1e-9);
        assertInUnitInterval(weight, 0, 3);
    }

    @Test
    public void zeroWeightMarksRejectedMeasurements() {
        // isolated_outlier: the outlier at index 2 is rejected outright.
        BarSeries series = seriesOf(10, 10.2, 50, 10.4);
        CorrentropyKalmanWeightIndicator weight = weight(new ClosePriceIndicator(series), 1e-3, 0.2, 2);

        Assert.assertEquals(1.0, weight.getValue(0).doubleValue(), 0.0);
        Assert.assertEquals(0.992622679915, weight.getValue(1).doubleValue(), 1e-9);
        Assert.assertEquals(0.0, weight.getValue(2).doubleValue(), 0.0);
        Assert.assertEquals(0.972381273952, weight.getValue(3).doubleValue(), 1e-9);
    }

    @Test
    public void growingImpulsesSaturateToZeroWeightAndRecover() {
        // increasing_impulse reference: indices 3 and 4 are beyond the kernel
        // saturation bound and are rejected with zero weight until the source
        // returns to values consistent with the predicted state.
        BarSeries series = seriesOf(10, 10.1, 12, 20, 50, 10.2);
        CorrentropyKalmanWeightIndicator weight = weight(new ClosePriceIndicator(series), 1e-3, 0.2, 2);

        Assert.assertEquals(0.998152080344, weight.getValue(1).doubleValue(), 1e-9);
        Assert.assertEquals(0.116922682100, weight.getValue(2).doubleValue(), 1e-9);
        Assert.assertEquals(0.0, weight.getValue(3).doubleValue(), 0.0);
        Assert.assertEquals(0.0, weight.getValue(4).doubleValue(), 0.0);
        Assert.assertEquals(0.999140908352, weight.getValue(5).doubleValue(), 1e-9);
    }

    @Test
    public void weightStaysInUnitIntervalOverImpulseRuns() {
        double[] data = { 10, 10.05, 10.05, 10.05, 10.5, 10.5, 10.5, 10.05, 12, 10.05, 10.05, 10.05, 15, 15, 15, 10.05,
                12, 10.05, 10.05, 10.05, 10.4 };
        BarSeries series = seriesOf(data);
        CorrentropyKalmanWeightIndicator weight = weight(new ClosePriceIndicator(series), 1e-4, 0.2, 2);

        assertInUnitInterval(weight, 0, 20);
        Assert.assertEquals(0.141967218392, weight.getValue(8).doubleValue(), 1e-9);
        Assert.assertEquals(5.080125477351625E-7, weight.getValue(12).doubleValue(), 1e-9);
        Assert.assertEquals(0.970011161794, weight.getValue(20).doubleValue(), 1e-9);
    }

    @Test
    public void nanWhenTheFilterStateIsUnavailable() {
        BarSeries series = seriesOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        MockIndicator source = new MockIndicator(series, 0, Arrays.asList(NaN.NaN, NaN.NaN, NaN.NaN, numOf(50),
                numOf(60), numOf(70), numOf(80), numOf(90), numOf(90), numOf(90)));
        CorrentropyKalmanWeightIndicator weight = weight(source, 1e-3, 10, 2);

        for (int index : new int[] { 0, 1, 2 }) {
            Assert.assertTrue("expected NaN at index " + index, weight.getValue(index).isNaN());
        }
        Assert.assertEquals(1.0, weight.getValue(3).doubleValue(), 0.0);
        Assert.assertEquals(0.306560818580, weight.getValue(4).doubleValue(), 1e-9);
        Assert.assertEquals(0.0, weight.getValue(7).doubleValue(), 0.0);

        // An invalid joint observation (zero process noise) leaves the filtered
        // state unavailable, so the weight is NaN at that index.
        BarSeries otherSeries = seriesOf(10, 10.2, 10.1, 10.3);
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(
                new ClosePriceIndicator(otherSeries), fixed(otherSeries, 1e-3, 0, 1e-3, 1e-3),
                constant(otherSeries, 1e-2), numOf(2));
        CorrentropyKalmanWeightIndicator otherWeight = filter.measurementWeight();

        Assert.assertEquals(1.0, otherWeight.getValue(0).doubleValue(), 0.0);
        Assert.assertTrue(otherWeight.getValue(1).isNaN());
        Assert.assertEquals(0.971871337085, otherWeight.getValue(2).doubleValue(), 1e-9);
    }

    @Test
    public void viewIsCachedAndSharesFilterSeriesAndUnstableBars() {
        BarSeries series = seriesOf(10, 10.2, 50, 10.4);
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(new ClosePriceIndicator(series),
                constant(series, 1e-3), constant(series, 0.2), numOf(2));

        CorrentropyKalmanWeightIndicator first = filter.measurementWeight();
        CorrentropyKalmanWeightIndicator second = filter.measurementWeight();
        Assert.assertSame(first, second);
        Assert.assertEquals(filter.getBarSeries(), first.getBarSeries());
        Assert.assertEquals(filter.getCountOfUnstableBars(), first.getCountOfUnstableBars());
    }

    @Test
    public void descriptorAndJsonRoundTrip() {
        BarSeries series = seriesOf(10, 10.2, 50, 10.4);
        // Non-default kernel bandwidth: the descriptor must carry it through the round trip.
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(new ClosePriceIndicator(series),
                constant(series, 1e-3), constant(series, 0.2), numOf(3.5));
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();

        ComponentDescriptor descriptor = weight.toDescriptor();
        Assert.assertEquals(1, descriptor.getComponents().size());
        Assert.assertEquals("CorrentropyKalmanFilterIndicator", descriptor.getComponents().get(0).getType());

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, descriptor);
        Indicator<?> jsonCopy = Indicator.fromJson(series, weight.toJson());

        Assert.assertEquals(descriptor, descriptorCopy.toDescriptor());
        Assert.assertEquals(descriptor, jsonCopy.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Assert.assertEquals(weight.getValue(i), descriptorCopy.getValue(i));
            Assert.assertEquals(weight.getValue(i), jsonCopy.getValue(i));
        }
    }
}
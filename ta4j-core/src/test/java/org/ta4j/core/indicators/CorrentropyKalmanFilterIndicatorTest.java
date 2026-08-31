/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for the {@link CorrentropyKalmanFilterIndicator} and its shared
 * {@link CorrentropyKalmanWeightIndicator} view. Expected values are reference
 * oracles produced by the covariance-whitened bounded fixed-point MCKF
 * algorithm of Chen et al. (2017) and agree across {@code DoubleNumFactory}
 * and {@code DecimalNumFactory} to the printed precision.
 */
public class CorrentropyKalmanFilterIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CorrentropyKalmanFilterIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private Indicator<Num> closePrice;

    @Before
    public void setUp() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 20, 22, 30, 50).build();
        closePrice = new ClosePriceIndicator(series);
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

    private CorrentropyKalmanFilterIndicator filter(Indicator<Num> source, double q, double r, double sigma) {
        BarSeries series = source.getBarSeries();
        return new CorrentropyKalmanFilterIndicator(source, constant(series, q), constant(series, r), numOf(sigma));
    }

    private CorrentropyKalmanFilterIndicator filter(Indicator<Num> source, double q, double r, double sigma,
            int maxIterations) {
        BarSeries series = source.getBarSeries();
        return new CorrentropyKalmanFilterIndicator(source, constant(series, q), constant(series, r), numOf(sigma),
                maxIterations);
    }

    private void assertValues(double[] expected, Indicator<Num> indicator, double tolerance) {
        Assert.assertEquals(expected.length, indicator.getBarSeries().getBarCount());
        for (int i = 0; i < expected.length; i++) {
            Num value = indicator.getValue(i);
            Assert.assertEquals("index " + i, expected[i], value.isNaN() ? Double.NaN : value.doubleValue(),
                    tolerance);
        }
    }

    private void assertNaNAt(Indicator<Num> indicator, int... indexes) {
        for (int index : indexes) {
            Assert.assertTrue("expected NaN at index " + index, indicator.getValue(index).isNaN());
        }
    }

    @Test
    public void firstValidObservationInitializesToMeasurementWithUnitWeight() {
        BarSeries series = seriesOf(10, 10, 10, 10);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-2, 2);

        assertValues(new double[] { 10.0, 10.0, 10.0, 10.0 }, filter, 0.0);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        assertValues(new double[] { 1.0, 1.0, 1.0, 1.0 }, weight, 0.0);
        assertValues(new double[] { 0.0, 0.0, 0.0, 0.0 }, filter.residual(), 0.0);
    }

    @Test
    public void stepResponseMatchesReference() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-2, 2);

        assertValues(new double[] { 10.0, 10.104986789380, 10.103075318788, 10.160640081483 }, filter, 1e-9);
        assertValues(new double[] { 1.0, 0.893290112001, 0.999881787167, 0.784455967796 }, filter.measurementWeight(),
                1e-9);
        assertValues(new double[] { 0.0, 0.095013, -0.003075, 0.139360 }, filter.residual(), 1e-5);
    }

    @Test
    public void isolatedOutlierIsRejectedByZeroKernelWeight() {
        BarSeries series = seriesOf(10, 10.2, 50, 10.4);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 0.2, 2);

        assertValues(new double[] { 10.0, 10.091153928197, 10.091153928197, 10.188312447274 }, filter, 1e-9);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        assertValues(new double[] { 1.0, 0.992622679915, 0.0, 0.972381304869 }, weight, 1e-9);
    }

    @Test
    public void tightMeasurementNoiseDownWeightsSmallInnovations() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-4, 2);

        assertValues(new double[] { 10.0, 10.0, 10.000007840644, 10.000007840644 }, filter, 1e-9);
        assertValues(new double[] { 1.0, 0.0, 0.000003733965, 0.0 }, filter.measurementWeight(), 1e-9);
    }

    @Test
    public void longOutlierRunDoesNotDragTheEstimate() {
        BarSeries series = seriesOf(10, 10.05, 10.05, 50);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-4, 0.2, 2);

        assertValues(new double[] { 10.0, 10.022733957334, 10.031262304655, 10.031262304655 }, filter, 1e-9);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        assertValues(new double[] { 1.0, 0.999535459756, 0.999780585808, 0.0 }, weight, 1e-9);
        assertValues(new double[] { 0.0, 0.027266, 0.018738, 39.968738 }, filter.residual(), 1e-5);
    }

    @Test
    public void outlierImpulsesStayBoundedAndTheFilterRecovers() {
        double[] data = { 10, 10.05, 10.05, 10.05, 10.5, 10.5, 10.5, 10.05, 12, 10.05, 10.05, 10.05, 15, 15, 15,
                10.05, 12, 10.05, 10.05, 10.05, 10.4 };
        BarSeries series = seriesOf(data);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-4, 0.2, 2);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();

        assertValues(new double[] { 10.0, 10.022733957334, 10.031262304655, 10.035732151355, 10.120255253383,
                10.179152423959, 10.222504176577, 10.201483141862, 10.232670340607, 10.213319965382, 10.197596546190,
                10.184564743779, 10.184564963020, 10.184565183484, 10.184565405171, 10.173416575413, 10.193205276834,
                10.182353600699, 10.172969142603, 10.164770622618, 10.179282410702 }, filter, 1e-9);
        Assert.assertEquals(0.141967257620, weight.getValue(8).doubleValue(), 1e-9);
        Assert.assertEquals(5.080128359294481E-7, weight.getValue(12).doubleValue(), 1e-9);
        Assert.assertEquals(0.970011202201, weight.getValue(20).doubleValue(), 1e-9);
    }

    @Test
    public void smoothSignalTracksWithoutLagBias() {
        double[] data = new double[21];
        for (int i = 0; i < 21; i++) {
            data[i] = 10 + 0.1 * Math.sin(2 * Math.PI * i / 20);
        }
        BarSeries series = seriesOf(data);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-2, 2);

        assertValues(new double[] { 10.0, 10.016118873347, 10.032437229180, 10.048153886507, 10.062098678015,
                10.072858660818, 10.079034330424, 10.079546559587, 10.073897525347, 10.062297739374, 10.045633049368,
                10.025314752042, 10.003093347412, 9.980902324269, 9.960737198500, 9.944513959880, 9.933860635005,
                9.929874793452, 9.932939112274, 9.942668579162, 9.957998226290 }, filter, 1e-9);
        Assert.assertEquals(0.974306162094, filter.measurementWeight().getValue(10).doubleValue(), 1e-9);
    }

    @Test
    public void nonConvergenceYieldsNaNThenRecovers() {
        double[] data = { 10, 10.05, 10.05, 10.05, 10.5, 10.5, 10.5, 10.05, 12, 10.05, 10.05, 10.05, 15, 15, 15,
                10.05, 12, 10.05, 10.05, 10.05, 10.4 };
        BarSeries series = seriesOf(data);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-4, 0.2, 2, 2);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();

        Assert.assertEquals(10.0, filter.getValue(12).doubleValue(), 0.0);
        Assert.assertEquals(10.0, filter.getValue(13).doubleValue(), 0.0);
        Assert.assertEquals(10.0, filter.getValue(14).doubleValue(), 0.0);
        Assert.assertEquals(0.0, weight.getValue(12).doubleValue(), 0.0);
        Assert.assertTrue(weight.getValue(1).isNaN());
        Assert.assertTrue(weight.getValue(15).isNaN());
        Assert.assertEquals(5.0, filter.residual().getValue(12).doubleValue(), 1e-6);
    }

    @Test
    public void underlyingNaNValuesAreSkippedAndStateRecovers() {
        BarSeries series = seriesOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        MockIndicator source = new MockIndicator(series, 0, Arrays.asList(NaN.NaN, NaN.NaN, NaN.NaN, numOf(50),
                numOf(60), numOf(70), numOf(80), numOf(90), numOf(90), numOf(90)));
        CorrentropyKalmanFilterIndicator filter = filter(source, 1e-3, 10, 2);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();

        assertNaNAt(filter, 0, 1, 2);
        assertNaNAt(weight, 0, 1, 2);
        Assert.assertEquals(50.0, filter.getValue(3).doubleValue(), 0.0);
        Assert.assertEquals(50.274409493107, filter.getValue(4).doubleValue(), 1e-9);
        Assert.assertEquals(50.287741547731, filter.getValue(5).doubleValue(), 1e-9);
        Assert.assertEquals(50.287783226356, filter.getValue(9).doubleValue(), 1e-9);
        Assert.assertEquals(1.0, weight.getValue(3).doubleValue(), 0.0);
        Assert.assertEquals(0.306560889133, weight.getValue(4).doubleValue(), 1e-9);
        Assert.assertEquals(0.000016124063, weight.getValue(6).doubleValue(), 1e-9);
        Assert.assertEquals(0.0, weight.getValue(7).doubleValue(), 0.0);
    }

    @Test
    public void invalidProcessNoiseIndexYieldsNaNThenRecovers() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        Indicator<Num> source = new ClosePriceIndicator(series);
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(source,
                fixed(series, 1e-3, 0, 1e-3, 1e-3), constant(series, 1e-2), numOf(2));

        Assert.assertEquals(10.0, filter.getValue(0).doubleValue(), 0.0);
        Assert.assertTrue(filter.getValue(1).isNaN());
        Assert.assertEquals(10.052223978787, filter.getValue(2).doubleValue(), 1e-9);
        Assert.assertEquals(10.136107118932, filter.getValue(3).doubleValue(), 1e-9);
    }

    @Test
    public void arithmeticOverflowKeepsDoubleNaNAndDecimalFinite() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        Indicator<Num> source = new ClosePriceIndicator(series);
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(source,
                constant(series, Double.MAX_VALUE), constant(series, Double.MAX_VALUE), numOf(2));

        if (numFactory == DoubleNumFactory.getInstance()) {
            Assert.assertEquals(10.0, filter.getValue(0).doubleValue(), 0.0);
            assertNaNAt(filter, 1, 2, 3);
        } else {
            Assert.assertEquals(10.0, filter.getValue(0).doubleValue(), 0.0);
            Assert.assertEquals(10.12, filter.getValue(1).doubleValue(), 1e-9);
            Assert.assertEquals(10.107692308, filter.getValue(2).doubleValue(), 1e-9);
            Assert.assertEquals(10.226470588, filter.getValue(3).doubleValue(), 1e-9);
        }
    }

    @Test
    public void rejectsNoiseIndicatorsOnDifferentSeries() {
        BarSeries otherSeries = seriesOf(1, 2, 3);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> new CorrentropyKalmanFilterIndicator(closePrice, constant(otherSeries, 1e-3),
                        constant(closePrice.getBarSeries(), 1e-2), numOf(2)));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> new CorrentropyKalmanFilterIndicator(closePrice, constant(closePrice.getBarSeries(), 1e-3),
                        constant(otherSeries, 1e-2), numOf(2)));
    }

    @Test
    public void unstableBarsAreTheMaximumOfTheConsumedIndicators() {
        BarSeries series = seriesOf(1, 2, 3);
        MockIndicator unstableSource = new MockIndicator(series, 3, Arrays.asList(numOf(1), numOf(2), numOf(3)));
        CorrentropyKalmanFilterIndicator filter = filter(unstableSource, 1e-3, 1e-2, 2);

        Assert.assertEquals(3, filter.getCountOfUnstableBars());
        Assert.assertEquals(3, filter.measurementWeight().getCountOfUnstableBars());
        Assert.assertEquals(0, filter(new ClosePriceIndicator(series), 1e-3, 1e-2, 2).getCountOfUnstableBars());
    }

    @Test
    public void weightIndicatorIsCachedAndSharesTheFilterSeries() {
        BarSeries series = seriesOf(10, 10.2, 50, 10.4);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 0.2, 2);

        CorrentropyKalmanWeightIndicator first = filter.measurementWeight();
        CorrentropyKalmanWeightIndicator second = filter.measurementWeight();
        Assert.assertSame(first, second);
        Assert.assertEquals(filter.getBarSeries(), first.getBarSeries());
        Assert.assertEquals(0.992622679915, first.getValue(1).doubleValue(), 1e-9);
        Assert.assertEquals(0.0, first.getValue(2).doubleValue(), 0.0);
    }

    @Test
    public void descriptorAndJsonRoundTrip() {
        BarSeries series = closePrice.getBarSeries();
        CorrentropyKalmanFilterIndicator original = new CorrentropyKalmanFilterIndicator(closePrice,
                constant(series, 1e-3), constant(series, 1e-2), numOf(2));
        ComponentDescriptor descriptor = original.toDescriptor();

        Assert.assertEquals(3, descriptor.getComponents().size());
        for (ComponentDescriptor component : descriptor.getComponents()) {
            Assert.assertNotEquals("CorrentropyKalmanWeightIndicator", component.getType());
        }

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, descriptor);
        Indicator<?> jsonCopy = Indicator.fromJson(series, original.toJson());

        Assert.assertEquals(descriptor, descriptorCopy.toDescriptor());
        Assert.assertEquals(descriptor, jsonCopy.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Assert.assertEquals(original.getValue(i), descriptorCopy.getValue(i));
            Assert.assertEquals(original.getValue(i), jsonCopy.getValue(i));
        }
    }
}

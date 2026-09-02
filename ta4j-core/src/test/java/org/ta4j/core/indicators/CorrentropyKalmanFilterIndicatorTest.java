/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;

import java.util.Arrays;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for the {@link CorrentropyKalmanFilterIndicator} and its shared
 * {@link CorrentropyKalmanWeightIndicator} view. Expected values are reference
 * oracles produced by the covariance-whitened bounded fixed-point MCKF
 * algorithm of Chen et al. (2017) and agree across {@code DoubleNumFactory} and
 * {@code DecimalNumFactory} to the printed precision. The reference values and
 * the fixture inputs live in
 * {@code src/test/resources/oracles/cf-558-mckf-reference-oracle.py} and the
 * regenerated {@code cf-558-mckf-reference-vectors.json}.
 */
public class CorrentropyKalmanFilterIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CorrentropyKalmanFilterIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private Indicator<Num> closePrice;

    @Before
    public void setUp() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 15, 20, 22, 30, 50)
                .build();
        closePrice = new ClosePriceIndicator(series);
    }

    private BarSeries seriesOf(double... data) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    private FixedIndicator<Num> constant(BarSeries series, double value) {
        Num[] values = new Num[series.getBarCount()];
        Arrays.fill(values, series.numFactory().numOf(value));
        return new FixedIndicator<>(series, values);
    }

    private FixedIndicator<Num> fixed(BarSeries series, double... values) {
        Num[] nums = new Num[values.length];
        for (int i = 0; i < values.length; i++) {
            nums[i] = series.numFactory().numOf(values[i]);
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
            Assert.assertEquals("index " + i, expected[i], value.isNaN() ? Double.NaN : value.doubleValue(), tolerance);
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

        assertValues(new double[] { 10.0, 10.104985931110, 10.103074789595, 10.160638902480 }, filter, 1e-9);
        assertValues(new double[] { 1.0, 0.893288290866, 0.999881827845, 0.784452745524 }, filter.measurementWeight(),
                1e-9);
        assertValues(new double[] { 0.0, 0.095014, -0.003075, 0.139361 }, filter.residual(), 1e-5);
    }

    @Test
    public void isolatedOutlierIsRejectedByZeroKernelWeight() {
        BarSeries series = seriesOf(10, 10.2, 50, 10.4);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 0.2, 2);

        assertValues(new double[] { 10.0, 10.091153928197, 10.091153928197, 10.188312327114 }, filter, 1e-9);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        assertValues(new double[] { 1.0, 0.992622679915, 0.0, 0.972381273952 }, weight, 1e-9);
    }

    @Test
    public void negativeImpulseIsRejectedSymmetrically() {
        BarSeries series = seriesOf(10, 10.2, -50, 10.4);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 0.2, 2);

        // Gaussian kernel symmetry: a negative impulse of the same magnitude
        // produces the same estimates and zero weight as the positive one.
        assertValues(new double[] { 10.0, 10.091153928197, 10.091153928197, 10.188312327114 }, filter, 1e-9);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        assertValues(new double[] { 1.0, 0.992622679915, 0.0, 0.972381273952 }, weight, 1e-9);
    }

    @Test
    public void tightMeasurementNoiseDownWeightsSmallInnovations() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-4, 2);

        assertValues(new double[] { 10.0, 10.0, 10.000007825322, 10.000007825322 }, filter, 1e-9);
        assertValues(new double[] { 1.0, 0.0, 0.000003733951, 0.0 }, filter.measurementWeight(), 1e-9);
    }

    @Test
    public void longOutlierRunDoesNotDragTheEstimate() {
        BarSeries series = seriesOf(10, 10.05, 10.05, 50);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-4, 0.2, 2);

        assertValues(new double[] { 10.0, 10.022733957334, 10.031262304100, 10.031262304100 }, filter, 1e-9);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        assertValues(new double[] { 1.0, 0.999535459756, 0.999780585795, 0.0 }, weight, 1e-9);
        assertValues(new double[] { 0.0, 0.027266, 0.018738, 39.968738 }, filter.residual(), 1e-5);
    }

    @Test
    public void outlierImpulsesStayBoundedAndTheFilterRecovers() {
        double[] data = { 10, 10.05, 10.05, 10.05, 10.5, 10.5, 10.5, 10.05, 12, 10.05, 10.05, 10.05, 15, 15, 15, 10.05,
                12, 10.05, 10.05, 10.05, 10.4 };
        BarSeries series = seriesOf(data);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-4, 0.2, 2);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();

        assertValues(new double[] { 10.0, 10.022733957334, 10.031262304100, 10.035732150932, 10.120255189011,
                10.179152267647, 10.222504041541, 10.201483022211, 10.232670215528, 10.213319852499, 10.197596443423,
                10.184564649533, 10.184564868773, 10.184565089237, 10.184565310925, 10.173416488557, 10.193205079389,
                10.182353416605, 10.172968970288, 10.164770460770, 10.179282259719 }, filter, 1e-9);
        Assert.assertEquals(0.141967218392, weight.getValue(8).doubleValue(), 1e-9);
        Assert.assertEquals(5.080125477351625E-7, weight.getValue(12).doubleValue(), 1e-9);
        Assert.assertEquals(0.970011161794, weight.getValue(20).doubleValue(), 1e-9);
    }

    @Test
    public void increasingImpulsePinsEstimateThenRecovers() {
        BarSeries series = seriesOf(10, 10.1, 12, 20, 50, 10.2);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 0.2, 2);
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();

        assertValues(new double[] { 10.0, 10.045599571087, 10.146897779664, 10.146897779664, 10.146897779664,
                10.162917154826 }, filter, 1e-9);
        // Growing impulses are progressively rejected: the weight saturates to
        // zero and the estimate is pinned at the last trusted level until the
        // source returns to values consistent with the predicted state.
        assertValues(new double[] { 1.0, 0.998152080344, 0.116922682100, 0.0, 0.0, 0.999140908352 }, weight, 1e-9);
        assertValues(new double[] { 0.0, 0.054400, 1.853102, 9.853102, 39.853102, 0.037083 }, filter.residual(), 1e-5);
    }

    @Test
    public void smoothSignalMatchesOracleReference() {
        // Reference semantics, not a quality claim: the filtered trajectory (with
        // its small lag at the sine peak) is pinned to the frozen oracle vectors.
        double[] data = new double[21];
        for (int i = 0; i < 21; i++) {
            data[i] = 10 + 0.1 * Math.sin(2 * Math.PI * i / 20);
        }
        BarSeries series = seriesOf(data);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-2, 2);

        assertValues(new double[] { 10.0, 10.016118873347, 10.032437195387, 10.048153794017, 10.062098563949,
                10.072858579041, 10.079034271334, 10.079546516708, 10.073897494087, 10.062297738754, 10.045633048826,
                10.025314799169, 10.003093489736, 9.980902535928, 9.960737399431, 9.944514346191, 9.933860930004,
                9.929875016925, 9.932939273320, 9.942668697381, 9.957998153004 }, filter, 1e-9);
        Assert.assertEquals(0.974306162697, filter.measurementWeight().getValue(10).doubleValue(), 1e-9);
    }

    @Test
    public void largeBandwidthConvergesToKalmanFilter() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1e-2, 1e6);

        // A huge kernel bandwidth makes the correntropy weight one and the MCKF
        // update collapse to the ordinary Kalman update. The reference values
        // agree with the KalmanFilterIndicator state recursion to ~1e-10 here.
        assertValues(new double[] { 10.0, 10.104311201552, 10.102658681866, 10.166958671113 }, filter, 1e-9);
        assertValues(new double[] { 1.0, 1.0, 1.0, 1.0 }, filter.measurementWeight(), 1e-9);

        KalmanFilterIndicator kalman = new KalmanFilterIndicator(new ClosePriceIndicator(series), 1e-3, 1e-2);
        for (int i = 0; i < series.getBarCount(); i++) {
            Assert.assertEquals("index " + i, kalman.getValue(i).doubleValue(), filter.getValue(i).doubleValue(), 1e-8);
        }
    }

    @Test
    public void nonConvergenceYieldsNaNThenRecovers() {
        double[] data = { 10, 10.05, 10.05, 10.05, 10.5, 10.5, 10.5, 10.05, 12, 10.05, 10.05, 10.05, 15, 15, 15, 10.05,
                12, 10.05, 10.05, 10.05, 10.4 };
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
        Assert.assertEquals(50.274408546555, filter.getValue(4).doubleValue(), 1e-9);
        Assert.assertEquals(50.287740597351, filter.getValue(5).doubleValue(), 1e-9);
        Assert.assertEquals(50.287782274663, filter.getValue(9).doubleValue(), 1e-9);
        Assert.assertEquals(1.0, weight.getValue(3).doubleValue(), 0.0);
        Assert.assertEquals(0.306560818580, weight.getValue(4).doubleValue(), 1e-9);
        Assert.assertEquals(0.000016124051, weight.getValue(6).doubleValue(), 1e-9);
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
        Assert.assertEquals(10.136103952807, filter.getValue(3).doubleValue(), 1e-9);
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
    public void kernelWhiteningAvoidsOverflowBeforeSquaring() {
        double measurement = 1.4e154;
        BarSeries series = seriesOf(0, measurement);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, Double.MAX_VALUE, 2);

        double normalizedError = measurement / Math.sqrt(Double.MAX_VALUE) / Math.sqrt(8.0);
        double expectedWeight = Math.exp(-normalizedError * normalizedError);
        Assert.assertEquals(expectedWeight, filter.measurementWeight().getValue(1).doubleValue(), 1e-12);
    }

    @Test
    public void finiteExtremeInnovationIsWhitenedBeforeSubtraction() {
        double extreme = 1e308;
        double bandwidth = 5e153;
        BarSeries series = seriesOf(-extreme, extreme);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, Double.MAX_VALUE,
                bandwidth);

        double endpoint = extreme / Math.sqrt(Double.MAX_VALUE) / Math.sqrt(2 * bandwidth * bandwidth);
        double expectedWeight = Math.exp(-4 * endpoint * endpoint);
        Assert.assertEquals(-extreme, filter.getValue(1).doubleValue(), 0.0);
        Assert.assertEquals(expectedWeight, filter.measurementWeight().getValue(1).doubleValue(), 1e-12);
    }

    @Test
    public void rejectsNoiseIndicatorsOnDifferentSeries() {
        BarSeries otherSeries = seriesOf(1, 2, 3);
        Assert.assertThrows(IllegalArgumentException.class, () -> new CorrentropyKalmanFilterIndicator(closePrice,
                constant(otherSeries, 1e-3), constant(closePrice.getBarSeries(), 1e-2), numOf(2)));
        Assert.assertThrows(IllegalArgumentException.class, () -> new CorrentropyKalmanFilterIndicator(closePrice,
                constant(closePrice.getBarSeries(), 1e-3), constant(otherSeries, 1e-2), numOf(2)));
    }

    @Test
    public void rejectsNullOrNonFiniteBandwidth() {
        BarSeries series = closePrice.getBarSeries();
        Indicator<Num> q = constant(series, 1e-3);
        Indicator<Num> r = constant(series, 1e-2);
        Assert.assertThrows(NullPointerException.class,
                () -> new CorrentropyKalmanFilterIndicator(closePrice, q, r, null));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> new CorrentropyKalmanFilterIndicator(closePrice, q, r, numOf(0.0)));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> new CorrentropyKalmanFilterIndicator(closePrice, q, r, numOf(-2.0)));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> new CorrentropyKalmanFilterIndicator(closePrice, q, r, NaN.NaN));
    }

    @Test
    public void normalizesBandwidthToSeriesNumFactory() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        NumFactory foreignFactory = numFactory == DoubleNumFactory.getInstance() ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(new ClosePriceIndicator(series),
                constant(series, 1e-3), constant(series, 1e-2), foreignFactory.numOf(2.0));

        assertValues(new double[] { 10.0, 10.104985931110, 10.103074789595, 10.160638902480 }, filter, 1e-9);
    }

    @Test
    public void normalizesDynamicInputsToSeriesNumFactory() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        NumFactory foreignFactory = numFactory == DoubleNumFactory.getInstance() ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        Indicator<Num> source = new FixedIndicator<>(series, foreignFactory.numOf(10), foreignFactory.numOf(10.2),
                foreignFactory.numOf(10.1), foreignFactory.numOf(10.3));
        Indicator<Num> processNoise = new FixedIndicator<>(series, foreignFactory.numOf(1e-3),
                foreignFactory.numOf(1e-3), foreignFactory.numOf(1e-3), foreignFactory.numOf(1e-3));
        Indicator<Num> measurementNoise = new FixedIndicator<>(series, foreignFactory.numOf(1e-2),
                foreignFactory.numOf(1e-2), foreignFactory.numOf(1e-2), foreignFactory.numOf(1e-2));
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(source, processNoise,
                measurementNoise, numOf(2));

        assertValues(new double[] { 10.0, 10.104985931110, 10.103074789595, 10.160638902480 }, filter, 1e-9);
    }

    @Test
    public void acceptsBandwidthsWhoseSquaredScaleIsNotRepresentableBySeriesFactory() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(10, 10.2, 10.1, 10.3)
                .build();
        Indicator<Num> source = new ClosePriceIndicator(series);
        Indicator<Num> q = constant(series, 1e-3);
        Indicator<Num> r = constant(series, 1e-2);
        NumFactory foreignFactory = DecimalNumFactory.getInstance();

        // A bandwidth whose square is not representable in the series NumFactory is
        // still accepted: only the converted bandwidth itself must stay finite and
        // positive. The tiny kernel rejects every subsequent innovation (the
        // estimate pins at the first value with zero weight).
        CorrentropyKalmanFilterIndicator pinned = new CorrentropyKalmanFilterIndicator(source, q, r,
                foreignFactory.numOf(1e-200));
        assertValues(new double[] { 10.0, 10.0, 10.0, 10.0 }, pinned, 0.0);
        assertValues(new double[] { 1.0, 0.0, 0.0, 0.0 }, pinned.measurementWeight(), 0.0);

        // A huge finite bandwidth converts cleanly and the MCKF update collapses to
        // the ordinary Kalman recursion (same oracle as the 1e6 collapse test).
        CorrentropyKalmanFilterIndicator collapsed = new CorrentropyKalmanFilterIndicator(source, q, r,
                foreignFactory.numOf(1e200));
        assertValues(new double[] { 10.0, 10.104311201552, 10.102658681866, 10.166958671113 }, collapsed, 1e-9);
        assertValues(new double[] { 1.0, 1.0, 1.0, 1.0 }, collapsed.measurementWeight(), 1e-9);
    }

    @Test
    public void nearDoubleLimitBandwidthKeepsExtremeResidualDownweighted() {
        // sigma close to Double.MAX_VALUE used to overflow the kernel scale
        // (sigma * sqrt(2)) and collapse every weight to one. The kernel must
        // normalize by sigma and sqrt(2) as separate factors so the whitened
        // residual keeps its genuine weight in (1/2, 1): the squared exponent
        // (2e308 / (2 * (1.7e308)^2) ~= 0.69) is bounded by 0.5 and sqrt(15).
        double extreme = 1e308;
        double sigma = 1.7e308;
        BarSeries series = seriesOf(-extreme, extreme);
        CorrentropyKalmanFilterIndicator filter = filter(new ClosePriceIndicator(series), 1e-3, 1.0, sigma, 1000);

        Num estimate = filter.getValue(1);
        Assert.assertFalse("estimate must stay finite at the extreme residual", estimate.isNaN());
        double weight = filter.measurementWeight().getValue(1).doubleValue();
        Assert.assertTrue("kernel must genuinely downweight the extreme residual, was " + weight,
                weight > 0.5 && weight < 1.0);
    }

    @Test
    public void recursiveStateStartsAtTheAbsoluteFirstStableIndex() {
        BarSeries series = seriesOf(1000, 1000, 10, 10.2, 50, 10.4);
        MockIndicator source = new MockIndicator(series, 2, numOf(1000), numOf(1000), numOf(10), numOf(10.2), numOf(50),
                numOf(10.4));
        CorrentropyKalmanFilterIndicator filter = filter(source, 1e-3, 0.2, 2);

        assertNaNAt(filter, 0, 1);
        assertNaNAt(filter.measurementWeight(), 0, 1);
        Assert.assertEquals(10.0, filter.getValue(2).doubleValue(), 0.0);
        Assert.assertEquals(1.0, filter.measurementWeight().getValue(2).doubleValue(), 0.0);
        Assert.assertEquals(10.091153928197, filter.getValue(3).doubleValue(), 1e-9);
        Assert.assertEquals(10.091153928197, filter.getValue(4).doubleValue(), 1e-9);
        Assert.assertEquals(10.188312327114, filter.getValue(5).doubleValue(), 1e-9);
    }

    @Test
    public void retainedSeriesInitializesWhenBeginIndexIsPastTheStableBoundary() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 10.1, 10.2, 10.3, 50, 10.4)
                .build();
        MockIndicator source = new MockIndicator(series, 3, numOf(10), numOf(10.1), numOf(10.2), numOf(10.3), numOf(50),
                numOf(10.4));
        Indicator<Num> q = constant(series, 1e-3);
        Indicator<Num> r = constant(series, 0.2);
        series.setMaximumBarCount(2);
        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(source, q, r, numOf(2));

        Assert.assertEquals(4, series.getBeginIndex());
        Assert.assertEquals(50.0, filter.getValue(4).doubleValue(), 0.0);
        Assert.assertEquals(1.0, filter.measurementWeight().getValue(4).doubleValue(), 0.0);
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
    public void residualSupportsSourcesThatExposeTheBackingSeries() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        Indicator<Num> source = new Indicator<>() {

            @Override
            public Num getValue(int index) {
                return series.getBar(index).getClosePrice();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }
        };
        CorrentropyKalmanFilterIndicator filter = filter(source, 1e-3, 1e-2, 2);

        assertValues(new double[] { 0.0, 0.095014, -0.003075, 0.139361 }, filter.residual(), 1e-5);
    }

    @Test
    public void sourceReadsStayBoundedAndCached() {
        BarSeries series = seriesOf(10, 10.2, 10.1, 10.3);
        CountingIndicator source = CountingIndicator.delegate(new ClosePriceIndicator(series));
        CorrentropyKalmanFilterIndicator filter = filter(source, 1e-3, 1e-2, 2);
        int barCount = series.getBarCount();

        for (int i = 0; i < barCount; i++) {
            filter.getValue(i);
            filter.measurementWeight().getValue(i);
        }
        Assert.assertTrue("filter + shared weight view should recompute the source at most once per bar: "
                + source.getCalculationCount(), source.getCalculationCount() <= barCount + 2);

        for (int i = 0; i < barCount; i++) {
            filter.residual().getValue(i);
        }
        Assert.assertTrue("residual adds at most one source recomputation per bar: " + source.getCalculationCount(),
                source.getCalculationCount() <= 2 * barCount + 4);

        for (int i = 0; i < barCount; i++) {
            filter.getValue(i);
        }
        Assert.assertTrue("residual re-reads each cached source value once: " + source.readCount(),
                source.readCount() <= 2 * barCount + 4);
        Assert.assertTrue("cached re-reads never recompute the source: " + source.getCalculationCount(),
                source.getCalculationCount() <= barCount + 2);
    }

    @Test
    public void descriptorAndJsonRoundTrip() {
        BarSeries series = closePrice.getBarSeries();
        // Non-default kernel bandwidth: the descriptor must carry it through the round
        // trip.
        CorrentropyKalmanFilterIndicator original = new CorrentropyKalmanFilterIndicator(closePrice,
                constant(series, 1e-3), constant(series, 1e-2), numOf(3.5));
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

    @Test
    public void oracleDiagnosticsPinConvergenceSemantics() throws Exception {
        JsonObject root;
        try (InputStream in = getClass().getResourceAsStream("/oracles/cf-558-mckf-reference-vectors.json")) {
            Assert.assertNotNull("oracle vectors resource must be on the test classpath", in);
            root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonArray fixtures = root.getAsJsonArray("fixtures");
        Assert.assertEquals(12, fixtures.size());
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonElement element : fixtures) {
            JsonObject fixture = element.getAsJsonObject();
            byName.put(fixture.get("name").getAsString(), fixture);
        }

        // step_response baseline: every index converges to the unique maximal fixed
        // point.
        assertDiagnostics(byName.get("step_response"), booleans(true, true, true, true),
                booleans(false, false, false, false), booleans(true, true, true, true), ints(0, 1, 1, 1));
        // isolated_outlier: index 2 is a saturated rejection (zero weight) and
        // its fixed point is not a maximum of the correntropy objective.
        assertDiagnostics(byName.get("isolated_outlier"), booleans(true, true, true, true),
                booleans(false, false, true, false), booleans(true, true, false, true), ints(0, 1, 2, 1));
        // tight_measurement_noise: R << P^- makes the objective bimodal; indices 1-3
        // settle at the predict-side stationary point while only index 0 is maximal.
        assertDiagnostics(byName.get("tight_measurement_noise"), booleans(true, true, true, true),
                booleans(false, true, false, true), booleans(true, false, false, false), ints(0, 2, 2, 2));
        // long_outlier_run: the final accumulated deviation saturates the kernel.
        assertDiagnostics(byName.get("long_outlier_run"), booleans(true, true, true, true),
                booleans(false, false, false, true), booleans(true, true, true, false), ints(0, 1, 1, 2));
        // increasing_impulse: growing deviations reject progressively until the
        // source returns to the trusted level.
        assertDiagnostics(byName.get("increasing_impulse"), booleans(true, true, true, true, true, true),
                booleans(false, false, false, true, true, false), booleans(true, true, false, false, false, true),
                ints(0, 1, 2, 2, 2, 1));
        // nan_warmup: the three leading invalid observations carry no diagnostics,
        // the following indices converge, and the saturated tail ends at weight zero.
        assertDiagnostics(byName.get("nan_warmup"),
                booleans(null, null, null, true, true, true, true, true, true, true),
                booleans(null, null, null, false, false, false, false, true, true, true),
                booleans(null, null, null, true, false, false, false, false, false, false),
                ints(null, null, null, 0, 2, 2, 2, 2, 2, 2));
        // large_bandwidth: the objective is flat; converged acceptances are mostly
        // non-maximal with large local-maxima counts.
        assertDiagnostics(byName.get("large_bandwidth"), booleans(true, true, true, true),
                booleans(false, false, false, false), booleans(true, false, false, false), ints(0, 1810, 5, 1043));
        // invalid_q_at_1: index 1 is skipped entirely (no diagnostics) because the
        // process noise is zero there.
        assertDiagnostics(byName.get("invalid_q_at_1"), booleans(true, null, true, true),
                booleans(false, null, false, false), booleans(true, null, true, true), ints(0, null, 1, 1));
        // overflow_double: only the first index converges; the overflow makes the
        // remaining indices unavailable and they carry no diagnostics.
        assertDiagnostics(byName.get("overflow_double"), booleans(true, null, null, null),
                booleans(false, null, null, null), booleans(true, null, null, null), ints(0, null, null, null));
    }

    private static void assertDiagnostics(JsonObject fixture, Boolean[] converged, Boolean[] saturated,
            Boolean[] maximal, Integer[] localMaxima) {
        Assert.assertArrayEquals(converged, boolArray(fixture.getAsJsonArray("converged")));
        Assert.assertArrayEquals(saturated, boolArray(fixture.getAsJsonArray("saturated_any")));
        Assert.assertArrayEquals(maximal, boolArray(fixture.getAsJsonArray("maximal")));
        Assert.assertArrayEquals(localMaxima, intArray(fixture.getAsJsonArray("local_maxima")));
    }

    private static Boolean[] booleans(Boolean... values) {
        return values;
    }

    private static Integer[] ints(Integer... values) {
        return values;
    }

    private static Boolean[] boolArray(JsonArray array) {
        Boolean[] out = new Boolean[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = array.get(i).isJsonNull() ? null : array.get(i).getAsBoolean();
        }
        return out;
    }

    private static Integer[] intArray(JsonArray array) {
        Integer[] out = new Integer[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = array.get(i).isJsonNull() ? null : array.get(i).getAsInt();
        }
        return out;
    }
}
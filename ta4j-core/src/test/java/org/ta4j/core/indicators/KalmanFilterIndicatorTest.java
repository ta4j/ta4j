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
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;

import java.util.Arrays;
import java.util.List;

public class KalmanFilterIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private Indicator<Num> closePrice;

    public KalmanFilterIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 20, 22, 30, 50).build();
        closePrice = new ClosePriceIndicator(series);
    }

    @Test
    public void testKalmanFilterIndicatorWithDefaultParameters() {
        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(closePrice);

        Assert.assertEquals(10.0, kalmanIndicator.getValue(0).doubleValue(), 1e-5);
        Assert.assertEquals(12.61791, kalmanIndicator.getValue(1).doubleValue(), 1e-5);
        Assert.assertEquals(15.45321, kalmanIndicator.getValue(2).doubleValue(), 1e-5);
        Assert.assertEquals(17.58865, kalmanIndicator.getValue(3).doubleValue(), 1e-5);
        Assert.assertEquals(21.29749, kalmanIndicator.getValue(4).doubleValue(), 1e-5);
        Assert.assertEquals(29.48101, kalmanIndicator.getValue(5).doubleValue(), 1e-5);
    }

    @Test
    public void testKalmanFilterIndicatorWithCustomParameters() {
        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(closePrice, 1e-4, 1e-6);

        Assert.assertEquals(10.0, kalmanIndicator.getValue(0).doubleValue(), 1e-5);
        Assert.assertEquals(14.95098, kalmanIndicator.getValue(1).doubleValue(), 1e-5);
        Assert.assertEquals(19.95049, kalmanIndicator.getValue(2).doubleValue(), 1e-5);
        Assert.assertEquals(21.97990, kalmanIndicator.getValue(3).doubleValue(), 1e-5);
        Assert.assertEquals(29.92136, kalmanIndicator.getValue(4).doubleValue(), 1e-5);
        Assert.assertEquals(49.80313, kalmanIndicator.getValue(5).doubleValue(), 1e-5);
    }

    @Test
    public void testKalmanFilterIndicatorWithNoiseAndOutliers() {
        BarSeries noiseSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10.0, 11.0, 100.0, 13.0, 14.0, 15.0)
                .build();
        Indicator<Num> noiseClosePrice = new ClosePriceIndicator(noiseSeries);

        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(noiseClosePrice, 1e-3, 1e-5);

        Assert.assertEquals(10.0, kalmanIndicator.getValue(0).doubleValue(), 1e-5);
        Assert.assertEquals(10.99019, kalmanIndicator.getValue(1).doubleValue(), 1e-5);
        Assert.assertEquals(99.12727, kalmanIndicator.getValue(2).doubleValue(), 1e-5);
        Assert.assertEquals(13.84446, kalmanIndicator.getValue(3).doubleValue(), 1e-5);
        Assert.assertEquals(13.99847, kalmanIndicator.getValue(4).doubleValue(), 1e-5);
        Assert.assertEquals(14.99018, kalmanIndicator.getValue(5).doubleValue(), 1e-5);
    }

    @Test
    public void testKalmanFilterIndicatorWithSingleBar() {
        BarSeries singleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10.0).build();
        Indicator<Num> singleBarClosePrice = new ClosePriceIndicator(singleBarSeries);

        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(singleBarClosePrice, 1e-3, 1e-5);

        Assert.assertEquals(10.0, kalmanIndicator.getValue(0).doubleValue(), 1e-5);
    }

    @Test
    public void testKalmanFilterIndicatorWithZeroBarData() {
        BarSeries emptySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(List.of()).build();
        Indicator<Num> emptySeriesClosePrice = new ClosePriceIndicator(emptySeries);

        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(emptySeriesClosePrice, 1e-3, 1e-5);

        Assert.assertEquals(NaN.NaN, kalmanIndicator.getValue(0));
    }

    @Test
    public void testUnstableBars() {
        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(closePrice);
        Assert.assertEquals(0, kalmanIndicator.getCountOfUnstableBars());
    }

    @Test
    public void dynamicNoiseUsesTheExactSourceIndex() {
        BarSeries series = closePrice.getBarSeries();
        FixedIndicator<Num> processNoise = new FixedIndicator<>(series, numOf(1e-4), numOf(100), numOf(1e-4),
                numOf(1e-4), numOf(1e-4), numOf(1e-4));
        FixedIndicator<Num> measurementNoise = new FixedIndicator<>(series, numOf(1e-3), numOf(1e-3), numOf(1e-3),
                numOf(1e-3), numOf(1e-3), numOf(1e-3));
        KalmanFilterIndicator dynamic = new KalmanFilterIndicator(closePrice, new KalmanNoiseIndicator(processNoise),
                new KalmanNoiseIndicator(measurementNoise));
        KalmanFilterIndicator baseline = new KalmanFilterIndicator(closePrice);

        Assert.assertEquals(baseline.getValue(0).doubleValue(), dynamic.getValue(0).doubleValue(), 1e-12);
        Assert.assertTrue(
                dynamic.getValue(1).minus(numOf(15)).abs().isLessThan(baseline.getValue(1).minus(numOf(15)).abs()));
    }

    @Test
    public void invalidDynamicNoiseDoesNotContaminateLaterState() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 30).build();
        Indicator<Num> source = new ClosePriceIndicator(series);
        FixedIndicator<Num> processNoise = new FixedIndicator<>(series, numOf(1e-4), numOf(0), numOf(1e-4));
        KalmanFilterIndicator dynamic = new KalmanFilterIndicator(source, new KalmanNoiseIndicator(processNoise),
                KalmanNoiseIndicator.constant(series, 1e-3));

        Assert.assertTrue(dynamic.getValue(1).isNaN());

        BarSeries comparisonSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 30).build();
        KalmanFilterIndicator comparison = new KalmanFilterIndicator(new ClosePriceIndicator(comparisonSeries));
        Assert.assertEquals(comparison.getValue(1).doubleValue(), dynamic.getValue(2).doubleValue(), 1e-12);
    }

    @Test
    public void rejectsInvalidStaticNoiseAndDifferentSeries() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new KalmanFilterIndicator(closePrice, 0, 1e-3));
        Assert.assertThrows(IllegalArgumentException.class, () -> new KalmanFilterIndicator(closePrice, 1e-4, -1));

        BarSeries otherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        KalmanNoiseIndicator otherNoise = KalmanNoiseIndicator.constant(otherSeries, 1e-4);
        Assert.assertThrows(IllegalArgumentException.class, () -> new KalmanFilterIndicator(closePrice, otherNoise,
                KalmanNoiseIndicator.constant(closePrice.getBarSeries(), 1e-3)));
    }

    @Test
    public void dynamicDescriptorAndJsonRoundTrip() {
        BarSeries series = closePrice.getBarSeries();
        KalmanFilterIndicator original = new KalmanFilterIndicator(closePrice,
                KalmanNoiseIndicator.constant(series, 1e-4), KalmanNoiseIndicator.constant(series, 1e-3));

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, original.toDescriptor());
        Indicator<?> jsonCopy = Indicator.fromJson(series, original.toJson());

        Assert.assertEquals(original.toDescriptor(), descriptorCopy.toDescriptor());
        Assert.assertEquals(original.toDescriptor(), jsonCopy.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Assert.assertEquals(original.getValue(i), descriptorCopy.getValue(i));
            Assert.assertEquals(original.getValue(i), jsonCopy.getValue(i));
        }
    }

    @Test
    public void legacyNumericNoiseDescriptorRemainsReadable() {
        ComponentDescriptor legacyDescriptor = ComponentDescriptor.builder()
                .withType("KalmanFilterIndicator")
                .withParameters(java.util.Map.of("processNoise", "0.0001", "measurementNoise", "0.001"))
                .addComponent(closePrice.toDescriptor())
                .build();

        Indicator<?> restored = IndicatorSerialization.fromDescriptor(closePrice.getBarSeries(), legacyDescriptor);
        KalmanFilterIndicator expected = new KalmanFilterIndicator(closePrice);

        Assert.assertTrue(restored instanceof KalmanFilterIndicator);
        for (int i = closePrice.getBarSeries().getBeginIndex(); i <= closePrice.getBarSeries().getEndIndex(); i++) {
            Assert.assertEquals(expected.getValue(i), restored.getValue(i));
        }
    }

    @Test
    public void testKalmanFilterIndicatorWithUnderlyingNaNValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                .build();
        MockIndicator mockRsi = new MockIndicator(series, 3, Arrays.asList(NaN.NaN, NaN.NaN, NaN.NaN, numOf(50),
                numOf(60), numOf(70), numOf(80), numOf(90), numOf(90), numOf(90)));

        KalmanFilterIndicator kalmanFilterIndicator = new KalmanFilterIndicator(mockRsi);

        // First three values should be NaN since underlying indicator returns NaN
        Assert.assertEquals(NaN.NaN, kalmanFilterIndicator.getValue(0));
        Assert.assertEquals(NaN.NaN, kalmanFilterIndicator.getValue(1));
        Assert.assertEquals(NaN.NaN, kalmanFilterIndicator.getValue(2));

        // Starting from index 3, the underlying indicator returns valid values,
        // so the Kalman filter should produce valid filtered values
        // The first valid value should be close to 50.0 (the first non-NaN underlying
        // value)
        Assert.assertEquals(49.95005, kalmanFilterIndicator.getValue(3).doubleValue(), 1e-5);
        Assert.assertEquals(55.21203, kalmanFilterIndicator.getValue(4).doubleValue(), 1e-5);
        Assert.assertEquals(60.89177, kalmanFilterIndicator.getValue(5).doubleValue(), 1e-5);
        Assert.assertEquals(67.12451, kalmanFilterIndicator.getValue(6).doubleValue(), 1e-5);
        Assert.assertEquals(73.96032, kalmanFilterIndicator.getValue(7).doubleValue(), 1e-5);
        Assert.assertEquals(78.53347, kalmanFilterIndicator.getValue(8).doubleValue(), 1e-5);
        Assert.assertEquals(81.72161, kalmanFilterIndicator.getValue(9).doubleValue(), 1e-5);
    }

    @Test
    public void highWatermarkThenReverseAccessDoesNotReplaySourceHistory() {
        int barCount = 256;
        double[] data = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            data[i] = 100 + i;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
        CountingIndicator source = CountingIndicator.delegate(new ClosePriceIndicator(series));
        KalmanFilterIndicator kalmanFilterIndicator = new KalmanFilterIndicator(source);

        kalmanFilterIndicator.getValue(barCount - 1);
        for (int i = barCount - 2; i >= 0; i--) {
            kalmanFilterIndicator.getValue(i);
        }

        Assert.assertTrue("High-watermark then reverse reads should reuse cached Kalman state",
                source.readCount() <= barCount + 2L);
    }

}

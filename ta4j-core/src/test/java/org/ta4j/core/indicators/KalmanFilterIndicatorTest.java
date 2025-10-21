/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
        BarSeries noiseSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10.0, 11.0, 100.0, 13.0, 14.0, 15.0).build();
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
    public void testKalmanFilterIndicatorWithUnderlyingNaNValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0).build();
        MockIndicator mockRsi = new MockIndicator(series, 3, Arrays.asList(NaN.NaN, NaN.NaN, NaN.NaN, numOf(50), numOf(60), numOf(70), numOf(80), numOf(90), numOf(90), numOf(90)));

        KalmanFilterIndicator kalmanFilterIndicator = new KalmanFilterIndicator(mockRsi);

        // First three values should be NaN since underlying indicator returns NaN
        Assert.assertEquals(NaN.NaN, kalmanFilterIndicator.getValue(0));
        Assert.assertEquals(NaN.NaN, kalmanFilterIndicator.getValue(1));
        Assert.assertEquals(NaN.NaN, kalmanFilterIndicator.getValue(2));

        // Starting from index 3, the underlying indicator returns valid values,
        // so the Kalman filter should produce valid filtered values
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(3));
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(4));
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(5));
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(6));
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(7));
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(8));
        Assert.assertNotEquals(NaN.NaN, kalmanFilterIndicator.getValue(9));

        // Verify that the filtered values are reasonable (close to the underlying
        // values)
        // The first valid value should be close to 50.0 (the first non-NaN underlying
        // value)
        Assert.assertEquals(50.0, kalmanFilterIndicator.getValue(3).doubleValue(), 0.1);
        // Subsequent values should be increasing as the underlying values increase
        Assert.assertTrue(kalmanFilterIndicator.getValue(4).doubleValue() > kalmanFilterIndicator.getValue(3).doubleValue());
        Assert.assertTrue(kalmanFilterIndicator.getValue(5).doubleValue() > kalmanFilterIndicator.getValue(4).doubleValue());
        // The filtered values should be close to the underlying values
        Assert.assertEquals(55.21, kalmanFilterIndicator.getValue(4).doubleValue(), 0.1);
        Assert.assertEquals(60.89, kalmanFilterIndicator.getValue(5).doubleValue(), 0.1);
    }
}

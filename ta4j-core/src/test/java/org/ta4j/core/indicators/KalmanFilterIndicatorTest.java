/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
        Assert.assertEquals(14.99500, kalmanIndicator.getValue(1).doubleValue(), 1e-5);
        Assert.assertEquals(17.61553, kalmanIndicator.getValue(2).doubleValue(), 1e-5);
        Assert.assertEquals(19.29951, kalmanIndicator.getValue(3).doubleValue(), 1e-5);
        Assert.assertEquals(22.78980, kalmanIndicator.getValue(4).doubleValue(), 1e-5);
        Assert.assertEquals(30.92094, kalmanIndicator.getValue(5).doubleValue(), 1e-5);
    }

    @Test
    public void testKalmanFilterIndicatorWithCustomParameters() {
        KalmanFilterIndicator kalmanIndicator = new KalmanFilterIndicator(closePrice, 1e-4, 1e-6);

        Assert.assertEquals(10.0, kalmanIndicator.getValue(0).doubleValue(), 1e-5);
        Assert.assertEquals(14.99999, kalmanIndicator.getValue(1).doubleValue(), 1e-5);
        Assert.assertEquals(19.95099, kalmanIndicator.getValue(2).doubleValue(), 1e-5);
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
        Assert.assertEquals(10.99999, kalmanIndicator.getValue(1).doubleValue(), 1e-5);
        Assert.assertEquals(99.12745, kalmanIndicator.getValue(2).doubleValue(), 1e-5);
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
        Assert.assertEquals(0, kalmanIndicator.getUnstableBars());
    }
}

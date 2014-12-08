/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.TADecimal;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.ConstantIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class CachedIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        series = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void ifCacheWorks() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(series), 3);
        TADecimal firstTime = sma.getValue(4);
        TADecimal secondTime = sma.getValue(4);
        assertEquals(firstTime, secondTime);
    }

    @Test
    public void getValueWithNullTimeSeries() {
        
        ConstantIndicator<TADecimal> constant = new ConstantIndicator<TADecimal>(TADecimal.TEN);
        assertEquals(TADecimal.TEN, constant.getValue(0));
        assertEquals(TADecimal.TEN, constant.getValue(100));
        assertNull(constant.getTimeSeries());

        SMAIndicator sma = new SMAIndicator(constant, 10);
        assertEquals(TADecimal.TEN, sma.getValue(0));
        assertEquals(TADecimal.TEN, sma.getValue(100));
        assertNull(sma.getTimeSeries());
    }

    @Test
    public void getValueWithCacheLengthIncrease() {
        double[] data = new double[200];
        Arrays.fill(data, 10);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(new MockTimeSeries(data)), 100);
        assertDecimalEquals(sma.getValue(105), 10);
    }

    @Test
    public void getValueWithOldResultsRemoval() {
        double[] data = new double[20];
        Arrays.fill(data, 1);
        TimeSeries timeSeries = new MockTimeSeries(data);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(timeSeries), 10);
        assertDecimalEquals(sma.getValue(5), 1);
        assertDecimalEquals(sma.getValue(10), 1);
        timeSeries.setMaximumTickCount(12);
        assertDecimalEquals(sma.getValue(19), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getValueOnRemovedResultShouldThrowException() {
        double[] data = new double[20];
        Arrays.fill(data, 1);
        TimeSeries timeSeries = new MockTimeSeries(data);
        timeSeries.setMaximumTickCount(12);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(timeSeries), 10);
        assertDecimalEquals(sma.getValue(19), 1);
        assertDecimalEquals(sma.getValue(18), 1);
        sma.getValue(17); // Here the iae should be thrown
    }
}

/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.trackers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class EMAIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {

        data = new MockTimeSeries(
                64.75, 63.79, 63.73,
                63.73, 63.55, 63.19,
                63.91, 63.85, 62.95,
                63.37, 61.33, 61.51);
    }

    @Test
    public void EMAUsingTimeFrame10UsingClosePrice() {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);

        assertDecimalEquals(ema.getValue(9), 63.6536);
        assertDecimalEquals(ema.getValue(10), 63.2312);
        assertDecimalEquals(ema.getValue(11), 62.9182);
    }

    @Test
    public void EMAFirstValueShouldBeEqualsToFirstDataValue() {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(ema.getValue(0), "64.75");
    }

    @Test
    public void valuesLessThanTimeFrameMustBeEqualsToSMAValues() {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 10);

        for (int i = 0; i < 9; i++) {
            assertEquals(sma.getValue(i), ema.getValue(i));
        }
    }
    
    @Test
    public void smallTimeFrame()
    {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(ema.getValue(0), "64.75");
    }
    
}

/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Marc de Verdelhan & respective authors (see AUTHORS)
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
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class EMAIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {

        data = new MockTimeSeries(
            22.2734, 22.1940, 22.0847, 22.1741, 22.1840,
            22.1344, 22.2337, 22.4323, 22.2436, 22.2933,
            22.1542, 22.3926, 22.3816, 22.6109, 23.3558,
            24.0519, 23.7530, 23.8324, 23.9516, 23.6338,
            23.8225, 23.8722, 23.6537, 23.1870, 23.0976,
            23.3260, 22.6805, 23.0976, 22.4025, 22.1725
        );
    }

    @Test
    public void emaUsingTimeFrame10UsingClosePrice() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(data);
        EMAIndicator ema = new EMAIndicator(closePrice, 10);

        assertEquals(ema.getValue(0), closePrice.getValue(0));
        
        assertDecimalEquals(ema.getValue(9), 22.254);
        assertDecimalEquals(ema.getValue(10), 22.2317);
        assertDecimalEquals(ema.getValue(11), 22.2572);
        assertDecimalEquals(ema.getValue(12), 22.2797);
        assertDecimalEquals(ema.getValue(13), 22.3399);
        assertDecimalEquals(ema.getValue(14), 22.5248);
        assertDecimalEquals(ema.getValue(15), 22.802);
        assertDecimalEquals(ema.getValue(16), 22.9726);
        assertDecimalEquals(ema.getValue(17), 23.1286);
        assertDecimalEquals(ema.getValue(18), 23.2772);
        assertDecimalEquals(ema.getValue(19), 23.3422);

    }
}

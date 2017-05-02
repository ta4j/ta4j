/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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

import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class RAVIIndicatorTest {

    private TimeSeries data;
    
    @Before
    public void setUp() {

        data = new MockTimeSeries(
                110.00, 109.27, 104.69, 107.07, 107.92,
                107.95, 108.70, 107.97, 106.09, 106.03,
                108.65, 109.54, 112.26, 114.38, 117.94
            
        );
    }
    
    @Test
    public void ravi() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(data);
        RAVIIndicator ravi = new RAVIIndicator(closePrice, 3, 8);
        
        assertDecimalEquals(ravi.getValue(0), 0);
        assertDecimalEquals(ravi.getValue(1), 0);
        assertDecimalEquals(ravi.getValue(2), 0);
        assertDecimalEquals(ravi.getValue(3), -0.6937);
        assertDecimalEquals(ravi.getValue(4), -1.1411);
        assertDecimalEquals(ravi.getValue(5), -0.1577);
        assertDecimalEquals(ravi.getValue(6), 0.229);
        assertDecimalEquals(ravi.getValue(7), 0.2412);
        assertDecimalEquals(ravi.getValue(8), 0.1202);
        assertDecimalEquals(ravi.getValue(9), -0.3324);
        assertDecimalEquals(ravi.getValue(10), -0.5804);
        assertDecimalEquals(ravi.getValue(11), 0.2013);
        assertDecimalEquals(ravi.getValue(12), 1.6156);
        assertDecimalEquals(ravi.getValue(13), 2.6167);
        assertDecimalEquals(ravi.getValue(14), 4.0799);
    }
}

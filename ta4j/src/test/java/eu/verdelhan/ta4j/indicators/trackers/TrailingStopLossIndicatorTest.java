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

import eu.verdelhan.ta4j.Decimal;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrailingStopLossIndicatorTest {
    
    private TimeSeries data;
    
    @Before
    public void setUp() {
        
        data = new MockTimeSeries(
            18, 19, 23, 22, 21,
            22, 17, 18, 21, 25,
            26, 29, 29, 28, 29,
            26, 25, 26, 26, 28
        );
    }
    
    @Test
    public void withoutInitialLimitUsingClosePrice() {
        ClosePriceIndicator price = new ClosePriceIndicator(data); 
        TrailingStopLossIndicator tsl = new TrailingStopLossIndicator(price, Decimal.valueOf(4));
        
        assertDecimalEquals(tsl.getValue(1), 15);
        assertDecimalEquals(tsl.getValue(2), 19);
        assertDecimalEquals(tsl.getValue(3), 19);

        assertDecimalEquals(tsl.getValue(8), 19);
        assertDecimalEquals(tsl.getValue(9), 21);
        assertDecimalEquals(tsl.getValue(10), 22);
        assertDecimalEquals(tsl.getValue(11), 25);
        assertDecimalEquals(tsl.getValue(12), 25);
    }
    
    @Test
    public void withInitialLimitUsingClosePrice() {
        ClosePriceIndicator price = new ClosePriceIndicator(data); 
        TrailingStopLossIndicator tsl = new TrailingStopLossIndicator(price,
                Decimal.valueOf(3), Decimal.valueOf(21));
        
        assertDecimalEquals(tsl.getValue(0), 21);
        assertDecimalEquals(tsl.getValue(1), 21);
        assertDecimalEquals(tsl.getValue(2), 21);

        assertDecimalEquals(tsl.getValue(8), 21);
        assertDecimalEquals(tsl.getValue(9), 22);
        assertDecimalEquals(tsl.getValue(10), 23);
        assertDecimalEquals(tsl.getValue(11), 26);
        assertDecimalEquals(tsl.getValue(12), 26);
        assertDecimalEquals(tsl.getValue(13), 26);
    }
    
}

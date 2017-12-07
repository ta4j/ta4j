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
package org.ta4j.core.indicators;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

public class DonchianChannelTest {

    private TimeSeries data;

    @Before
    public void setUp() {
    	    // Index pos:             0  1  2  3  4  5  6  7  8  9 10 11 12 
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }
    
    /**
     * Test the donchian channel with a length of 1
     */
    @Test
    public void testUpperDonchianChannel1() {
        DonchianChannelUpper dcu = new DonchianChannelUpper(new ClosePriceIndicator(data), 1);
        
	    for(int i = 0; i < data.getEndIndex(); i++) {
	        assertDecimalEquals(dcu.getValue(i), data.getTick(i).getClosePrice().toDouble());
	    }
    }
    
    /**
     * Test the donchian channel with a length of 1
     */
    @Test
    public void testLowerDonchianChannel1() {
        DonchianChannelLower dcl = new DonchianChannelLower(new ClosePriceIndicator(data), 1);
        
	    for(int i = 0; i < data.getEndIndex(); i++) {
	        assertDecimalEquals(dcl.getValue(i), data.getTick(i).getClosePrice().toDouble());
	    }
    }
    
    /**
     * Test the donchian channel with a length of 3
     */
    @Test
    public void testUpperDonchianChannel3() {
        DonchianChannelUpper dcu = new DonchianChannelUpper(new ClosePriceIndicator(data), 3);
        
        assertDecimalEquals(dcu.getValue(0), 1);
        assertDecimalEquals(dcu.getValue(1), 2);
        assertDecimalEquals(dcu.getValue(2), 3);
        assertDecimalEquals(dcu.getValue(3), 4);
        assertDecimalEquals(dcu.getValue(4), 4);
        assertDecimalEquals(dcu.getValue(5), 4);
        assertDecimalEquals(dcu.getValue(6), 5);
        assertDecimalEquals(dcu.getValue(7), 5);
        assertDecimalEquals(dcu.getValue(8), 5);
        assertDecimalEquals(dcu.getValue(9), 4);
        assertDecimalEquals(dcu.getValue(10), 4);
        assertDecimalEquals(dcu.getValue(11), 4);
        assertDecimalEquals(dcu.getValue(12), 4);
    }
    
    /**
     * Test the donchian channel with a length of 3
     */
    @Test
    public void testLowerDonchianChannel3() {
        DonchianChannelLower dcl = new DonchianChannelLower(new ClosePriceIndicator(data), 3);
        
        assertDecimalEquals(dcl.getValue(0), 1);
        assertDecimalEquals(dcl.getValue(1), 1);
        assertDecimalEquals(dcl.getValue(2), 1);
        assertDecimalEquals(dcl.getValue(3), 2);
        assertDecimalEquals(dcl.getValue(4), 3);
        assertDecimalEquals(dcl.getValue(5), 3);
        assertDecimalEquals(dcl.getValue(6), 3);
        assertDecimalEquals(dcl.getValue(7), 4);
        assertDecimalEquals(dcl.getValue(8), 3);
        assertDecimalEquals(dcl.getValue(9), 3);
        assertDecimalEquals(dcl.getValue(10), 3);
        assertDecimalEquals(dcl.getValue(11), 3);
        assertDecimalEquals(dcl.getValue(12), 2);
    }
    
    /**
     * Test the donchian channel with a length of 20
     */
    @Test
    public void testUpperDonchianChannel20() {
        DonchianChannelUpper dcu = new DonchianChannelUpper(new ClosePriceIndicator(data), 20);
        
        assertDecimalEquals(dcu.getValue(0), 1);
        assertDecimalEquals(dcu.getValue(1), 2);
        assertDecimalEquals(dcu.getValue(2), 3);
        assertDecimalEquals(dcu.getValue(3), 4);
        assertDecimalEquals(dcu.getValue(4), 4);
        assertDecimalEquals(dcu.getValue(5), 4);
        assertDecimalEquals(dcu.getValue(6), 5);
        assertDecimalEquals(dcu.getValue(7), 5);
        assertDecimalEquals(dcu.getValue(8), 5);
        assertDecimalEquals(dcu.getValue(9), 5);
        assertDecimalEquals(dcu.getValue(10), 5);
        assertDecimalEquals(dcu.getValue(11), 5);
        assertDecimalEquals(dcu.getValue(12), 5);
    }
    
    /**
     * Test the donchian channel with a length of 20
     */
    @Test
    public void testLowerDonchianChannel20() {
        DonchianChannelLower dcl = new DonchianChannelLower(new ClosePriceIndicator(data), 20);
        
        assertDecimalEquals(dcl.getValue(0), 1);
        assertDecimalEquals(dcl.getValue(1), 1);
        assertDecimalEquals(dcl.getValue(2), 1);
        assertDecimalEquals(dcl.getValue(3), 1);
        assertDecimalEquals(dcl.getValue(4), 1);
        assertDecimalEquals(dcl.getValue(5), 1);
        assertDecimalEquals(dcl.getValue(6), 1);
        assertDecimalEquals(dcl.getValue(7), 1);
        assertDecimalEquals(dcl.getValue(8), 1);
        assertDecimalEquals(dcl.getValue(9), 1);
        assertDecimalEquals(dcl.getValue(10), 1);
        assertDecimalEquals(dcl.getValue(11), 1);
        assertDecimalEquals(dcl.getValue(12), 1);
    }
	
}

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
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Decimal;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockTick;
import org.ta4j.core.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class DojiIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        List<Tick> ticks = new ArrayList<Tick>();
        // open, close, high, low
        ticks.add(new MockTick(19, 19, 22, 16));
        ticks.add(new MockTick(10, 18, 20, 10));
        ticks.add(new MockTick(17, 20, 21, 17));
        ticks.add(new MockTick(15, 15.1, 16, 14));
        ticks.add(new MockTick(15, 11, 15, 8));
        ticks.add(new MockTick(11, 12, 12, 10));
        series = new MockTimeSeries(ticks);
    }
    
    @Test
    public void getValueAtIndex0() {
        DojiIndicator doji = new DojiIndicator(new MockTimeSeries(0d), 10, Decimal.valueOf("0.03"));
        assertTrue(doji.getValue(0));
        
        doji = new DojiIndicator(new MockTimeSeries(1d), 10, Decimal.valueOf("0.03"));
        assertFalse(doji.getValue(0));
    }
    
    @Test
    public void getValue() {
        DojiIndicator doji = new DojiIndicator(series, 3, Decimal.valueOf("0.1"));
        assertTrue(doji.getValue(0));
        assertFalse(doji.getValue(1));
        assertFalse(doji.getValue(2));
        assertTrue(doji.getValue(3));
        assertFalse(doji.getValue(4));
        assertFalse(doji.getValue(5));
    }
}

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
package eu.verdelhan.ta4j.indicators.candles;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ThreeBlackCrowsIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        List<Tick> ticks = new ArrayList<Tick>();
        // open, close, high, low
        ticks.add(new MockTick(19, 19, 22, 15));
        ticks.add(new MockTick(10, 18, 20, 8));
        ticks.add(new MockTick(17, 20, 21, 17));
        ticks.add(new MockTick(19, 17, 20, 16.9));
        ticks.add(new MockTick(17.5, 14, 18, 13.9));
        ticks.add(new MockTick(15, 11, 15, 11));
        ticks.add(new MockTick(12, 14, 15, 8));
        ticks.add(new MockTick(13, 16, 16, 11));
        series = new MockTimeSeries(ticks);
    }
    
    @Test
    public void getValue() {
        ThreeBlackCrowsIndicator tbc = new ThreeBlackCrowsIndicator(series, 3, Decimal.valueOf("0.1"));
        assertFalse(tbc.getValue(0));
        assertFalse(tbc.getValue(1));
        assertFalse(tbc.getValue(2));
        assertFalse(tbc.getValue(3));
        assertFalse(tbc.getValue(4));
        assertTrue(tbc.getValue(5));
        assertFalse(tbc.getValue(6));
        assertFalse(tbc.getValue(7));
    }
}

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

public class ThreeWhiteSoldiersIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        List<Tick> ticks = new ArrayList<Tick>();
        // open, close, high, low
        ticks.add(new MockTick(19, 19, 22, 15));
        ticks.add(new MockTick(10, 18, 20, 8));
        ticks.add(new MockTick(17, 16, 21, 15));
        ticks.add(new MockTick(15.6, 18, 18.1, 14));
        ticks.add(new MockTick(16, 19.9, 20, 15));
        ticks.add(new MockTick(16.8, 23, 23, 16.7));
        ticks.add(new MockTick(17, 25, 25, 17));
        ticks.add(new MockTick(23, 16.8, 24, 15));
        series = new MockTimeSeries(ticks);
    }
    
    @Test
    public void getValue() {
        ThreeWhiteSoldiersIndicator tws = new ThreeWhiteSoldiersIndicator(series, 3, Decimal.valueOf("0.1"));
        assertFalse(tws.getValue(0));
        assertFalse(tws.getValue(1));
        assertFalse(tws.getValue(2));
        assertFalse(tws.getValue(3));
        assertFalse(tws.getValue(4));
        assertTrue(tws.getValue(5));
        assertFalse(tws.getValue(6));
        assertFalse(tws.getValue(7));
    }
}

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
package eu.verdelhan.ta4j.indicators.helpers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CloseLocationValueIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        List<Tick> ticks = new ArrayList<Tick>();
        // open, close, high, low
        ticks.add(new MockTick(10, 18, 20, 10));
        ticks.add(new MockTick(17, 20, 21, 17));
        ticks.add(new MockTick(15, 15, 16, 14));
        ticks.add(new MockTick(15, 11, 15, 8));
        ticks.add(new MockTick(11, 12, 12, 10));
        series = new MockTimeSeries(ticks);
    }
    
    @Test
    public void getValue() {
        CloseLocationValueIndicator clv = new CloseLocationValueIndicator(series);
        assertDecimalEquals(clv.getValue(0), 0.6);
        assertDecimalEquals(clv.getValue(1), 0.5);
        assertDecimalEquals(clv.getValue(2), 0);
        assertDecimalEquals(clv.getValue(3), -1d/7);
        assertDecimalEquals(clv.getValue(4), 1);
    }
}

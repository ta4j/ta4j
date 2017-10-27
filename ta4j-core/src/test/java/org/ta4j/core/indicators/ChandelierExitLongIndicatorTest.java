/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Decimal;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockTick;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class ChandelierExitLongIndicatorTest {
    
    private TimeSeries data;

    @Before
    public void setUp() {
        List<Tick> ticks = new ArrayList<Tick>();
        // open, close, high, low
        ticks.add(new MockTick(44.98, 45.05, 45.17, 44.96));
        ticks.add(new MockTick(45.05, 45.10, 45.15, 44.99));
        ticks.add(new MockTick(45.11, 45.19, 45.32, 45.11));
        ticks.add(new MockTick(45.19, 45.14, 45.25, 45.04));
        ticks.add(new MockTick(45.12, 45.15, 45.20, 45.10));
        ticks.add(new MockTick(45.15, 45.14, 45.20, 45.10));
        ticks.add(new MockTick(45.13, 45.10, 45.16, 45.07));
        ticks.add(new MockTick(45.12, 45.15, 45.22, 45.10));
        ticks.add(new MockTick(45.15, 45.22, 45.27, 45.14));
        ticks.add(new MockTick(45.24, 45.43, 45.45, 45.20));
        ticks.add(new MockTick(45.43, 45.44, 45.50, 45.39));
        ticks.add(new MockTick(45.43, 45.55, 45.60, 45.35));
        ticks.add(new MockTick(45.58, 45.55, 45.61, 45.39));
        ticks.add(new MockTick(45.45, 45.01, 45.55, 44.80));
        ticks.add(new MockTick(45.03, 44.23, 45.04, 44.17));

        data = new BaseTimeSeries(ticks);
    }

    @Test
    public void massIndexUsing3And8TimeFrames() {
        ChandelierExitLongIndicator cel = new ChandelierExitLongIndicator(data, 5, Decimal.TWO);

        assertDecimalEquals(cel.getValue(5), 44.4676);
        assertDecimalEquals(cel.getValue(6), 44.6021);
        assertDecimalEquals(cel.getValue(7), 44.6277);
        assertDecimalEquals(cel.getValue(8), 44.7201);
        assertDecimalEquals(cel.getValue(9), 44.9101);
        assertDecimalEquals(cel.getValue(10), 45.0241);
        assertDecimalEquals(cel.getValue(11), 45.1193);
        assertDecimalEquals(cel.getValue(12), 45.1374);
        assertDecimalEquals(cel.getValue(13), 44.9319);
        assertDecimalEquals(cel.getValue(14), 44.7195);
    }
}

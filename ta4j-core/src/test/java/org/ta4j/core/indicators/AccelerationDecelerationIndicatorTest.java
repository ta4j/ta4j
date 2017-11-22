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
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class AccelerationDecelerationIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {

        List<Bar> bars = new ArrayList<Bar>();

        bars.add(new MockBar(0, 0, 16, 8));
        bars.add(new MockBar(0, 0, 12, 6));
        bars.add(new MockBar(0, 0, 18, 14));
        bars.add(new MockBar(0, 0, 10, 6));
        bars.add(new MockBar(0, 0, 8, 4));

        series = new MockTimeSeries(bars);
    }

    @Test
    public void calculateWithSma2AndSma3() {
        AccelerationDecelerationIndicator acceleration = new AccelerationDecelerationIndicator(series, 2, 3);

        assertDecimalEquals(acceleration.getValue(0), 0);
        assertDecimalEquals(acceleration.getValue(1), 0);
        assertDecimalEquals(acceleration.getValue(2), 0.08333333333);
        assertDecimalEquals(acceleration.getValue(3), 0.41666666666);
        assertDecimalEquals(acceleration.getValue(4), -2);
    }

    @Test
    public void withSma1AndSma2() {
        AccelerationDecelerationIndicator acceleration = new AccelerationDecelerationIndicator(series, 1, 2);

        assertDecimalEquals(acceleration.getValue(0), 0);
        assertDecimalEquals(acceleration.getValue(1), 0);
        assertDecimalEquals(acceleration.getValue(2), 0);
        assertDecimalEquals(acceleration.getValue(3), 0);
        assertDecimalEquals(acceleration.getValue(4), 0);
    }

    @Test
    public void withSmaDefault() {
        AccelerationDecelerationIndicator acceleration = new AccelerationDecelerationIndicator(series);

        assertDecimalEquals(acceleration.getValue(0), 0);
        assertDecimalEquals(acceleration.getValue(1), 0);
        assertDecimalEquals(acceleration.getValue(2), 0);
        assertDecimalEquals(acceleration.getValue(3), 0);
        assertDecimalEquals(acceleration.getValue(4), 0);
    }
}

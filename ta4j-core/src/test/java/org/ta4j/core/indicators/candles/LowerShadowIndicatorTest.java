/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

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
package org.ta4j.core.indicators.candles;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

public class LowerShadowIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(10, 18, 20, 10));
        bars.add(new MockBar(17, 20, 21, 17));
        bars.add(new MockBar(15, 15, 16, 14));
        bars.add(new MockBar(15, 11, 15, 8));
        bars.add(new MockBar(11, 12, 12, 10));
        series = new MockTimeSeries(bars);
    }

    @Test
    public void getValue() {
        LowerShadowIndicator lowerShadow = new LowerShadowIndicator(series);
        TATestsUtils.assertNumEquals(lowerShadow.getValue(0), 0);
        TATestsUtils.assertNumEquals(lowerShadow.getValue(1), 0);
        TATestsUtils.assertNumEquals(lowerShadow.getValue(2), 1);
        TATestsUtils.assertNumEquals(lowerShadow.getValue(3), 3);
        TATestsUtils.assertNumEquals(lowerShadow.getValue(4), 1);
    }
}

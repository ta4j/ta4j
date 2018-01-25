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
package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class StandardErrorIndicatorTest {
    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(10, 20, 30, 40, 50, 40, 40, 50, 40, 30, 20, 10);
    }

    @Test
    public void usingTimeFrame5UsingClosePrice() {
        StandardErrorIndicator se = new StandardErrorIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(se.getValue(0), 0);
        assertNumEquals(se.getValue(1), 3.5355);
        assertNumEquals(se.getValue(2), 4.714);
        assertNumEquals(se.getValue(3), 5.5902);
        assertNumEquals(se.getValue(4), 6.3246);
        assertNumEquals(se.getValue(5), 4.5607);
        assertNumEquals(se.getValue(6), 2.8284);
        assertNumEquals(se.getValue(7), 2.1909);
        assertNumEquals(se.getValue(8), 2.1909);
        assertNumEquals(se.getValue(9), 2.8284);
        assertNumEquals(se.getValue(10), 4.5607);
        assertNumEquals(se.getValue(11), 6.3246);
    }

    @Test
    public void shouldBeZeroWhenTimeFrameIs1() {
        StandardErrorIndicator se = new StandardErrorIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(se.getValue(1), 0);
        assertNumEquals(se.getValue(3), 0);
    }
}

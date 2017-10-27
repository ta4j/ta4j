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
package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class StandardDeviationIndicatorTest {
    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9);
    }

    @Test
    public void standardDeviationUsingTimeFrame4UsingClosePrice() {
        StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);

        assertDecimalEquals(sdv.getValue(0), 0);
        assertDecimalEquals(sdv.getValue(1), Math.sqrt(0.25));
        assertDecimalEquals(sdv.getValue(2), Math.sqrt(2.0/3));
        assertDecimalEquals(sdv.getValue(3), Math.sqrt(1.25));
        assertDecimalEquals(sdv.getValue(4), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(5), Math.sqrt(0.25));
        assertDecimalEquals(sdv.getValue(6), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(7), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(8), Math.sqrt(0.5));
        assertDecimalEquals(sdv.getValue(9), Math.sqrt(3.5));
        assertDecimalEquals(sdv.getValue(10), Math.sqrt(10.5));
    }

    @Test
    public void standardDeviationShouldBeZeroWhenTimeFrameIs1() {
        StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(sdv.getValue(3), 0);
        assertDecimalEquals(sdv.getValue(8), 0);
    }
}

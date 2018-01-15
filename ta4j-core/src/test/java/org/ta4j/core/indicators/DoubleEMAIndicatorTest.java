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
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class DoubleEMAIndicatorTest {

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        TimeSeries data = new MockTimeSeries(
                0.73, 0.72, 0.86, 0.72, 0.62,
                0.76, 0.84, 0.69, 0.65, 0.71,
                0.53, 0.73, 0.77, 0.67, 0.68
        );
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void doubleEMAUsingTimeFrame5UsingClosePrice() {
        DoubleEMAIndicator doubleEma = new DoubleEMAIndicator(closePrice, 5);

        assertDecimalEquals(doubleEma.getValue(0), 0.73);
        assertDecimalEquals(doubleEma.getValue(1), 0.7244);
        assertDecimalEquals(doubleEma.getValue(2), 0.7992);

        assertDecimalEquals(doubleEma.getValue(6), 0.7858);
        assertDecimalEquals(doubleEma.getValue(7), 0.7374);
        assertDecimalEquals(doubleEma.getValue(8), 0.6884);

        assertDecimalEquals(doubleEma.getValue(12), 0.7184);
        assertDecimalEquals(doubleEma.getValue(13), 0.6939);
        assertDecimalEquals(doubleEma.getValue(14), 0.6859);
    }
}

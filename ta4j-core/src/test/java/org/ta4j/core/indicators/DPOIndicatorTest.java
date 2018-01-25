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
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class DPOIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        series = new MockTimeSeries(
                22.27, 22.19, 22.08, 22.17, 22.18, 22.13,
                22.23, 22.43, 22.24, 22.29, 22.15, 22.39,
                22.38, 22.61, 23.36, 24.05, 23.75, 23.83,
                23.95, 23.63, 23.82, 23.87, 23.65, 23.19,
                23.10, 23.33, 22.68, 23.10, 22.40, 22.17,
                22.27, 22.19, 22.08, 22.17, 22.18, 22.13,
                22.23, 22.43, 22.24, 22.29, 22.15, 22.39,
                22.38, 22.61, 23.36, 24.05, 23.75, 23.83,
                23.95, 23.63, 23.82, 23.87, 23.65, 23.19,
                23.10, 23.33, 22.68, 23.10, 22.40, 22.17,
                22.27, 22.19, 22.08, 22.17, 22.18, 22.13,
                22.23, 22.43, 22.24, 22.29, 22.15, 22.39,
                22.38, 22.61, 23.36, 24.05, 23.75, 23.83,
                23.95, 23.63, 23.82, 23.87, 23.65, 23.19,
                23.10, 23.33, 22.68, 23.10, 22.40, 22.17
        );
    }

    @Test
    public void dpo() {
        DPOIndicator dpo = new DPOIndicator(series, 9);
        ClosePriceIndicator cp = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(cp,9);
        int timeShift = 9/2+1;
        for (int i = series.getBeginIndex(); i<= series.getEndIndex(); i++) {
            assertEquals(dpo.getValue(i), cp.getValue(i).minus(sma.getValue(i-timeShift)));
        }

        assertNumEquals(dpo.getValue(9), 0.111999);
        assertNumEquals(dpo.getValue(10), -0.02);
        assertNumEquals(dpo.getValue(11), 0.21142857142);
        assertNumEquals(dpo.getValue(12), 0.169999999999999);
    }


    @Test(expected = IndexOutOfBoundsException.class)
    public void dpoIOOBE() {
        DPOIndicator dpo = new DPOIndicator(series, 9);
        dpo.getValue(100);
    }
}

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
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static org.ta4j.core.TATestsUtils.assertNumEquals;

public class MeanDeviationIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9);
    }

    @Test
    public void meanDeviationUsingTimeFrame5UsingClosePrice() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(meanDeviation.getValue(2), 2.44444444444444);
        assertNumEquals(meanDeviation.getValue(3), 2.5);
        assertNumEquals(meanDeviation.getValue(7), 2.16);
        assertNumEquals(meanDeviation.getValue(8), 2.32);
        assertNumEquals(meanDeviation.getValue(9), 2.72);
    }

    @Test
    public void firstValueShouldBeZero() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);
        TATestsUtils.assertNumEquals(meanDeviation.getValue(0), 0);
    }

    @Test
    public void meanDeviationShouldBeZeroWhenTimeFrameIs1() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 1);
        TATestsUtils.assertNumEquals(meanDeviation.getValue(2), 0);
        TATestsUtils.assertNumEquals(meanDeviation.getValue(7), 0);
    }
}

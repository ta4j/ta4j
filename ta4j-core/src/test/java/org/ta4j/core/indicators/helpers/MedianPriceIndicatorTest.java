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
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class MedianPriceIndicatorTest {
    private MedianPriceIndicator average;

    TimeSeries timeSeries;

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();

        bars.add(new MockBar(0, 0, 16, 8));
        bars.add(new MockBar(0, 0, 12, 6));
        bars.add(new MockBar(0, 0, 18, 14));
        bars.add(new MockBar(0, 0, 10, 6));
        bars.add(new MockBar(0, 0, 32, 6));
        bars.add(new MockBar(0, 0, 2, 2));
        bars.add(new MockBar(0, 0, 0, 0));
        bars.add(new MockBar(0, 0, 8, 1));
        bars.add(new MockBar(0, 0, 83, 32));
        bars.add(new MockBar(0, 0, 9, 3));


        this.timeSeries = new MockTimeSeries(bars);
        average = new MedianPriceIndicator(timeSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        Decimal result;
        for (int i = 0; i < 10; i++) {
            result = timeSeries.getBar(i).getMaxPrice().plus(timeSeries.getBar(i).getMinPrice())
                    .dividedBy(Decimal.TWO);
            assertEquals(average.getValue(i), result);
        }
    }
}

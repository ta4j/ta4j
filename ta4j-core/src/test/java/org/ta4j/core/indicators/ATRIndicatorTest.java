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

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.IndicatorTest;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.*;

public class ATRIndicatorTest extends IndicatorTest {

    public ATRIndicatorTest() throws Exception {
        super((data, params) -> { return new ATRIndicator((TimeSeries) data, (int) params[0]); },
              "ATR.xls",
              7);
    }

    @Test
    public void getValue() {
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(0, 12, 15, 8));
        bars.add(new MockBar(0, 8, 11, 6));
        bars.add(new MockBar(0, 15, 17, 14));
        bars.add(new MockBar(0, 15, 17, 14));
        bars.add(new MockBar(0, 0, 0, 2));
        Indicator<Decimal> actualIndicator = testIndicator(new MockTimeSeries(bars), 3);

        assertEquals(7d, actualIndicator.getValue(0).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(6d / 3 + (1 - 1d / 3) * actualIndicator.getValue(0).doubleValue(),
                actualIndicator.getValue(1).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(9d / 3 + (1 - 1d / 3) * actualIndicator.getValue(1).doubleValue(),
                actualIndicator.getValue(2).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(3d / 3 + (1 - 1d / 3) * actualIndicator.getValue(2).doubleValue(),
                actualIndicator.getValue(3).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(15d / 3 + (1 - 1d / 3) * actualIndicator.getValue(3).doubleValue(),
                actualIndicator.getValue(4).doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        TimeSeries series = getSeries();
        Indicator<Decimal> actualIndicator;

        actualIndicator = testIndicator(series, 1);
        assertIndicatorEquals(getIndicator(1), actualIndicator); 
        assertEquals(4.8, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = testIndicator(series, 3);
        assertIndicatorEquals(getIndicator(3), actualIndicator); 
        assertEquals(7.4225, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = testIndicator(series, 13);
        assertIndicatorEquals(getIndicator(13), actualIndicator); 
        assertEquals(8.8082, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

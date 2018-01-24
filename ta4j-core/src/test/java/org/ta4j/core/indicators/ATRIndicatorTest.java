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
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.assertIndicatorEquals;

public class ATRIndicatorTest extends IndicatorTest<TimeSeries, Decimal> {

    private ExternalIndicatorTest xls;

    public ATRIndicatorTest() throws Exception {
        super((data, params) -> new ATRIndicator(data, (int) params[0]));
        xls = new XLSIndicatorTest(this.getClass(), "ATR.xls", 7);
    }

    @Test
    public void testDummy() throws Exception {
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(0, 12, 15, 8));
        bars.add(new MockBar(0, 8, 11, 6));
        bars.add(new MockBar(0, 15, 17, 14));
        bars.add(new MockBar(0, 15, 17, 14));
        bars.add(new MockBar(0, 0, 0, 2));
        Indicator<Decimal> indicator = getIndicator(new MockTimeSeries(bars), 3);

        assertEquals(7d, indicator.getValue(0).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(6d / 3 + (1 - 1d / 3) * indicator.getValue(0).doubleValue(),
                indicator.getValue(1).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(9d / 3 + (1 - 1d / 3) * indicator.getValue(1).doubleValue(),
                indicator.getValue(2).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(3d / 3 + (1 - 1d / 3) * indicator.getValue(2).doubleValue(),
                indicator.getValue(3).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(15d / 3 + (1 - 1d / 3) * indicator.getValue(3).doubleValue(),
                indicator.getValue(4).doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void testXls() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Decimal> indicator;

        indicator = getIndicator(xlsSeries, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(4.8, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(7.4225, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(8.8082, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

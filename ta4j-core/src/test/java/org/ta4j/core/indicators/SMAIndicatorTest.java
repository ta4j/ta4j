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
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class SMAIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void SMAUsingTimeFrame3UsingClosePrice() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);

        assertDecimalEquals(sma.getValue(0), 1);
        assertDecimalEquals(sma.getValue(1), 1.5);
        assertDecimalEquals(sma.getValue(2), 2);
        assertDecimalEquals(sma.getValue(3), 3);
        assertDecimalEquals(sma.getValue(4), 10d / 3);
        assertDecimalEquals(sma.getValue(5), 11d / 3);
        assertDecimalEquals(sma.getValue(6), 4);
        assertDecimalEquals(sma.getValue(7), 13d / 3);
        assertDecimalEquals(sma.getValue(8), 4);
        assertDecimalEquals(sma.getValue(9), 10d / 3);
        assertDecimalEquals(sma.getValue(10), 10d / 3);
        assertDecimalEquals(sma.getValue(11), 10d / 3);
        assertDecimalEquals(sma.getValue(12), 3);
    }

    @Test
    public void SMAWhenTimeFrameIs1ResultShouldBeIndicatorValue() {
        SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), quoteSMA.getValue(i));
        }
    }

    private void smaXls(int timeFrame) throws Exception {
        // compare values computed by indicator
        // with values computed independently in excel
        XlsTestsUtils.testXlsIndicator(SMAIndicatorTest.class, "SMA.xls", 6, (inputSeries) -> {
            return new SMAIndicator(new ClosePriceIndicator(inputSeries), timeFrame);
        }, timeFrame);
    }

    @Test
    public void smaXls1() throws Exception {
        smaXls(1);
    }

    @Test
    public void smaXls3() throws Exception {
        smaXls(3);
    }

    @Test
    public void smaXls13() throws Exception {
        smaXls(13);
    }
}

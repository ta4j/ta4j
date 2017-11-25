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
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class EMAIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                64.75, 63.79, 63.73,
                63.73, 63.55, 63.19,
                63.91, 63.85, 62.95,
                63.37, 61.33, 61.51);
    }

    @Test
    public void emaFirstValueShouldBeEqualsToFirstDataValue() {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(ema.getValue(0), 64.75);
    }

    @Test
    public void emaUsingTimeFrame10UsingClosePrice() {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(ema.getValue(9), 63.6948);
        assertDecimalEquals(ema.getValue(10), 63.2648);
        assertDecimalEquals(ema.getValue(11), 62.9457);
    }

    @Test
    public void stackOverflowError() {
        List<Bar> bigListOfBars = new ArrayList<Bar>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(bigSeries);
        EMAIndicator ema = new EMAIndicator(closePrice, 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator does not work as intended.
        assertDecimalEquals(ema.getValue(9999), 9994.5);
    }

    private void emaXls(int timeFrame) throws Exception {
        // compare values computed by indicator
        // with values computed independently in excel
        XlsTestsUtils.testXlsIndicator(EMAIndicatorTest.class, "EMA.xls", timeFrame, 6, (inputSeries) -> {
            return new EMAIndicator(new ClosePriceIndicator(inputSeries), timeFrame);
        });
    }

    @Test
    public void emaXls1() throws Exception {
        emaXls(1);
    }

    @Test
    public void emaXls3() throws Exception {
        emaXls(3);
    }

    @Test
    public void emaXls13() throws Exception {
        emaXls(13);
    }
}

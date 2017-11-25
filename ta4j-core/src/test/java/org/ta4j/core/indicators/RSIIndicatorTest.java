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
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class RSIIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                50.45, 50.30, 50.20,
                50.15, 50.05, 50.06,
                50.10, 50.08, 50.03,
                50.07, 50.01, 50.14,
                50.22, 50.43, 50.50,
                50.56, 50.52, 50.70,
                50.55, 50.62, 50.90,
                50.82, 50.86, 51.20,
                51.30, 51.10);
    }

    @Test
    public void rsiFirstValueShouldBeZero() {
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 14);
        assertEquals(Decimal.ZERO, rsi.getValue(0));
    }

    @Test
    public void rsiHundredIfNoLoss() {
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 1);
        assertEquals(Decimal.HUNDRED, rsi.getValue(14));
        assertEquals(Decimal.HUNDRED, rsi.getValue(15));
    }

    @Test
    public void rsiUsingTimeFrame14UsingClosePrice() {
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 14);
        assertDecimalEquals(rsi.getValue(15), 68.4746);
        assertDecimalEquals(rsi.getValue(16), 64.7836);
        assertDecimalEquals(rsi.getValue(17), 72.0776);
        assertDecimalEquals(rsi.getValue(18), 60.7800);
        assertDecimalEquals(rsi.getValue(19), 63.6439);
        assertDecimalEquals(rsi.getValue(20), 72.3433);
        assertDecimalEquals(rsi.getValue(21), 67.3822);
        assertDecimalEquals(rsi.getValue(22), 68.5438);
        assertDecimalEquals(rsi.getValue(23), 76.2770);
        assertDecimalEquals(rsi.getValue(24), 77.9908);
        assertDecimalEquals(rsi.getValue(25), 67.4895);
    }

    private void rsiXls(int timeFrame) throws Exception {
        // compare values computed by indicator
        // with values computed independently in excel
        XlsTestsUtils.testXlsIndicator(RSIIndicatorTest.class, "RSI.xls", timeFrame, 10, (inputSeries) -> {
            return new RSIIndicator(new ClosePriceIndicator(inputSeries), timeFrame);
        });
    }

    @Test
    public void rsiXls1() throws Exception {
        rsiXls(1);
    }

    @Test
    public void rsiXls3() throws Exception {
        rsiXls(3);
    }

    @Test
    public void rsiXls13() throws Exception {
        rsiXls(13);
    }
}

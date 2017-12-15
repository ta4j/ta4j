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
import org.ta4j.core.Indicator;
import org.ta4j.core.IndicatorTest;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class RSIIndicatorTest extends IndicatorTest {
    
    public RSIIndicatorTest() throws Exception {
        super(RSIIndicator.class, "RSI.xls", 10);
    }

    private TimeSeries data;

    @Before
    public void setUp() throws Exception {
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
    public void firstValueShouldBeZero() throws Exception {
        Indicator<Decimal> indicator = TestIndicator(data, 14);
        assertEquals(Decimal.ZERO, indicator.getValue(0));
    }

    @Test
    public void rsiHundredIfNoLoss() throws Exception {
        Indicator<Decimal> indicator = TestIndicator(data, 1);
        assertEquals(Decimal.HUNDRED, indicator.getValue(14));
        assertEquals(Decimal.HUNDRED, indicator.getValue(15));
    }

    @Test
    public void rsiUsingTimeFrame14UsingClosePrice() throws Exception {
        Indicator<Decimal> indicator = TestIndicator(data, 14);
        assertDecimalEquals(indicator.getValue(15), 68.4746);
        assertDecimalEquals(indicator.getValue(16), 64.7836);
        assertDecimalEquals(indicator.getValue(17), 72.0776);
        assertDecimalEquals(indicator.getValue(18), 60.7800);
        assertDecimalEquals(indicator.getValue(19), 63.6439);
        assertDecimalEquals(indicator.getValue(20), 72.3433);
        assertDecimalEquals(indicator.getValue(21), 67.3822);
        assertDecimalEquals(indicator.getValue(22), 68.5438);
        assertDecimalEquals(indicator.getValue(23), 76.2770);
        assertDecimalEquals(indicator.getValue(24), 77.9908);
        assertDecimalEquals(indicator.getValue(25), 67.4895);
    }

    @Test
    public void xlsTest() throws Exception {
        TimeSeries xlsSeries = getXlsSeries();
        assertIndicatorEquals(XlsIndicator(1), TestIndicator(xlsSeries, 1));
        assertIndicatorEquals(XlsIndicator(3), TestIndicator(xlsSeries, 3));
        assertIndicatorEquals(XlsIndicator(13), TestIndicator(xlsSeries, 13));
    }
}

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

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.assertIndicatorEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Decimal;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.indicators.IndicatorTest;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

public class RSIIndicatorTest extends IndicatorTest {

    private TimeSeries data;
    private ExternalIndicatorTest xls;
    //private ExternalIndicatorTest sql;

    public RSIIndicatorTest() {
        super((data, params) -> { return new RSIIndicator((Indicator<Decimal>) data, (int) params[0]); });
        xls = new XLSIndicatorTest(this.getClass(), "RSI.xls", 10);
        //sql = new SQLIndicatorTest(this.getClass(), "RSI.db", username, pass, table, column);
    }

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
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 14);
        assertEquals(Decimal.ZERO, indicator.getValue(0));
    }

    @Test
    public void hundredIfNoLoss() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertEquals(Decimal.HUNDRED, indicator.getValue(14));
        assertEquals(Decimal.HUNDRED, indicator.getValue(15));
    }

    @Test
    public void zeroIfNoGain() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertEquals(Decimal.ZERO, indicator.getValue(1));
        assertEquals(Decimal.ZERO, indicator.getValue(2));
    }

    @Test
    public void usingTimeFrame14UsingClosePrice() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 14);
        assertEquals(68.4746, indicator.getValue(15).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(64.7836, indicator.getValue(16).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(72.0776, indicator.getValue(17).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(60.7800, indicator.getValue(18).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(63.6439, indicator.getValue(19).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(72.3433, indicator.getValue(20).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(67.3822, indicator.getValue(21).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(68.5438, indicator.getValue(22).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(76.2770, indicator.getValue(23).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(77.9908, indicator.getValue(24).doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(67.4895, indicator.getValue(25).doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void xlsTest() throws Exception {
        Indicator<Decimal> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Decimal> indicator;

        indicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator); 
        assertEquals(100.0, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        indicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator); 
        assertEquals(67.0453, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        indicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator); 
        assertEquals(52.5876, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

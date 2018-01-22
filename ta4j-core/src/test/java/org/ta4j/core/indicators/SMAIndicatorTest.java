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
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;
import static org.ta4j.core.TATestsUtils.assertIndicatorEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Decimal;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;

public class SMAIndicatorTest extends IndicatorTest<Indicator<Decimal>, Decimal> {

    private ExternalIndicatorTest xls;

    public SMAIndicatorTest() throws Exception {
        super((data, params) -> new SMAIndicator((Indicator<Decimal>) data, (int) params[0]));
        xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6);
    }

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void usingTimeFrame3UsingClosePrice() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 3);

        assertDecimalEquals(indicator.getValue(0), 1);
        assertDecimalEquals(indicator.getValue(1), 1.5);
        assertDecimalEquals(indicator.getValue(2), 2);
        assertDecimalEquals(indicator.getValue(3), 3);
        assertDecimalEquals(indicator.getValue(4), 10d / 3);
        assertDecimalEquals(indicator.getValue(5), 11d / 3);
        assertDecimalEquals(indicator.getValue(6), 4);
        assertDecimalEquals(indicator.getValue(7), 13d / 3);
        assertDecimalEquals(indicator.getValue(8), 4);
        assertDecimalEquals(indicator.getValue(9), 10d / 3);
        assertDecimalEquals(indicator.getValue(10), 10d / 3);
        assertDecimalEquals(indicator.getValue(11), 10d / 3);
        assertDecimalEquals(indicator.getValue(12), 3);
    }

    @Test
    public void whenTimeFrameIs1ResultShouldBeIndicatorValue() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), indicator.getValue(i));
        }
    }

    @Test
    public void externalData() throws Exception {
        Indicator<Decimal> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Decimal> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), actualIndicator);
        assertEquals(329.0, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), actualIndicator);
        assertEquals(326.6333, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), actualIndicator);
        assertEquals(327.7846, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

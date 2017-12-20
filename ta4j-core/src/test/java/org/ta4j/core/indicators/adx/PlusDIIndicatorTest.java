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
package org.ta4j.core.indicators.adx;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.assertIndicatorEquals;

import org.junit.Test;
import org.ta4j.core.Decimal;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;

public class PlusDIIndicatorTest extends IndicatorTest {

    private ExternalIndicatorTest xls;
    
    public PlusDIIndicatorTest() throws Exception {
        super((data, params) -> { return new PlusDIIndicator((TimeSeries) data, (int) params[0]); });
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 12);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Decimal> actualIndicator;

        actualIndicator = getIndicator(xlsSeries, 1);
        assertIndicatorEquals(xls.getIndicator(1), actualIndicator); 
        assertEquals(12.5, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = getIndicator(xlsSeries, 3);
        assertIndicatorEquals(xls.getIndicator(3), actualIndicator); 
        assertEquals(22.8407, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = getIndicator(xlsSeries, 13);
        assertIndicatorEquals(xls.getIndicator(13), actualIndicator); 
        assertEquals(22.1399, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

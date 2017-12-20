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

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

public class EMAIndicatorTest extends IndicatorTest {

    private ExternalIndicatorTest xls;

    public EMAIndicatorTest() throws Exception {
        super((data, params) -> { return new EMAIndicator((Indicator<Decimal>) data, (int) params[0]); });
        xls = new XLSIndicatorTest(this.getClass(), "EMA.xls", 6);
    }
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
    public void firstValueShouldBeEqualsToFirstDataValue() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(indicator.getValue(0), 64.75);
    }

    @Test
    public void usingTimeFrame10UsingClosePrice() throws Exception {
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(indicator.getValue(9), 63.6948);
        assertDecimalEquals(indicator.getValue(10), 63.2648);
        assertDecimalEquals(indicator.getValue(11), 62.9457);
    }

    @Test
    public void stackOverflowError() throws Exception {
        List<Bar> bigListOfBars = new ArrayList<Bar>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        Indicator<Decimal> indicator = getIndicator(new ClosePriceIndicator(bigSeries), 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator does not work as intended.
        assertDecimalEquals(indicator.getValue(9999), 9994.5);
    }

    @Test
    public void externalData() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        Indicator<Decimal> closePrice = new ClosePriceIndicator(xlsSeries);
        Indicator<Decimal> indicator;
        
        indicator = getIndicator(closePrice, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(329.0, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        indicator = getIndicator(closePrice, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(327.7748, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        indicator = getIndicator(closePrice, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(327.4076, indicator.getValue(indicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

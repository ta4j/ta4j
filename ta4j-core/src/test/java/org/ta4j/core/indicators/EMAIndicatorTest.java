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
import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.IndicatorTest;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.*;

public class EMAIndicatorTest extends IndicatorTest {

    public EMAIndicatorTest() throws Exception {
        super((data, params) -> { return new EMAIndicator((Indicator<Decimal>) data, (int) params[0]); },
              "EMA.xls",
              6);
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
    public void emaFirstValueShouldBeEqualsToFirstDataValue() {
        Indicator<Decimal> actualIndicator = testIndicator(new ClosePriceIndicator(data), 1);
        assertDecimalEquals(actualIndicator.getValue(0), 64.75);
    }

    @Test
    public void emaUsingTimeFrame10UsingClosePrice() {
        Indicator<Decimal> actualIndicator = testIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(actualIndicator.getValue(9), 63.6948);
        assertDecimalEquals(actualIndicator.getValue(10), 63.2648);
        assertDecimalEquals(actualIndicator.getValue(11), 62.9457);
    }

    @Test
    public void stackOverflowError() {
        List<Bar> bigListOfBars = new ArrayList<Bar>();
        for (int i = 0; i < 10000; i++) {
            bigListOfBars.add(new MockBar(i));
        }
        MockTimeSeries bigSeries = new MockTimeSeries(bigListOfBars);
        Indicator<Decimal> actualIndicator = testIndicator(new ClosePriceIndicator(bigSeries), 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator does not work as intended.
        assertDecimalEquals(actualIndicator.getValue(9999), 9994.5);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        TimeSeries series = getSeries();
        Indicator<Decimal> closePrice = new ClosePriceIndicator(series);
        Indicator<Decimal> actualIndicator;
        
        actualIndicator = testIndicator(closePrice, 1);
        assertIndicatorEquals(getIndicator(1), actualIndicator);
        assertEquals(329.0, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = testIndicator(closePrice, 3);
        assertIndicatorEquals(getIndicator(3), actualIndicator);
        assertEquals(327.7748, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);

        actualIndicator = testIndicator(closePrice, 13);
        assertIndicatorEquals(getIndicator(13), actualIndicator);
        assertEquals(327.4076, actualIndicator.getValue(actualIndicator.getTimeSeries().getEndIndex()).doubleValue(), TATestsUtils.TA_OFFSET);
    }

}

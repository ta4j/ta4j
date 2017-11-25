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
import org.ta4j.core.Bar;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class ATRIndicatorTest {

    @Test
    public void getValue() {
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(0, 12, 15, 8));
        bars.add(new MockBar(0, 8, 11, 6));
        bars.add(new MockBar(0, 15, 17, 14));
        bars.add(new MockBar(0, 15, 17, 14));
        bars.add(new MockBar(0, 0, 0, 2));
        ATRIndicator atr = new ATRIndicator(new MockTimeSeries(bars), 3);


        assertDecimalEquals(atr.getValue(0), 7d);
        assertDecimalEquals(atr.getValue(1), 6d / 3 + (1 - 1d / 3) * atr.getValue(0).toDouble());
        assertDecimalEquals(atr.getValue(2), 9d / 3 + (1 - 1d / 3) * atr.getValue(1).toDouble());
        assertDecimalEquals(atr.getValue(3), 3d / 3 + (1 - 1d / 3) * atr.getValue(2).toDouble());
        assertDecimalEquals(atr.getValue(4), 15d / 3 + (1 - 1d / 3) * atr.getValue(3).toDouble());
    }

    private void atrXls(int timeFrame) throws Exception {
        // compare values computed by indicator
        // with values computed independently in excel
        XlsTestsUtils.testXlsIndicator(ATRIndicatorTest.class, "ATR.xls", timeFrame, 7, (inputSeries) -> {
            return new ATRIndicator(inputSeries, timeFrame);
        });
    }

    @Test
    public void atrXls1() throws Exception {
        atrXls(1);
    }

    @Test
    public void atrXls3() throws Exception {
        atrXls(3);
    }

    @Test
    public void atrXls13() throws Exception {
        atrXls(13);
    }
}

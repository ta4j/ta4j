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

import org.junit.Test;
import org.ta4j.core.XlsTestsUtils;

public class PlusDIIndicatorTest {

    private void plusDIXls(int timeFrame) throws Exception {
        // compare values computed by indicator
        // with values computed independently in excel
        XlsTestsUtils.testXlsIndicator(PlusDIIndicatorTest.class, "ADX.xls", 12, (inputSeries) -> {
            return new PlusDIIndicator(inputSeries, timeFrame);
        }, timeFrame);
    }

    @Test
    public void plusDIXls1() throws Exception {
        plusDIXls(1);
    }

    @Test
    public void plusDIXls3() throws Exception {
        plusDIXls(3);
    }

    @Test
    public void plusDIXls13() throws Exception {
        plusDIXls(13);
    }
}

/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

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
import org.ta4j.core.Indicator;
import org.ta4j.core.Num.Num;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.function.Function;

public class StochasticRSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{

    private TimeSeries data;

    public StochasticRSIIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction,50.45, 50.30, 50.20, 50.15, 50.05, 50.06,
                50.10, 50.08, 50.03, 50.07, 50.01, 50.14, 50.22, 50.43, 50.50,
                50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86, 51.20,
                51.30, 51.10);
    }

    @Test
    public void stochasticRSI() {
        StochasticRSIIndicator srsi = new StochasticRSIIndicator(data, 14);
        TestUtils.assertNumEquals(srsi.getValue(15), 1);
        TestUtils.assertNumEquals(srsi.getValue(16), 0.9460);
        TestUtils.assertNumEquals(srsi.getValue(17), 1);
        TestUtils.assertNumEquals(srsi.getValue(18), 0.8365);
        TestUtils.assertNumEquals(srsi.getValue(19), 0.8610);
        TestUtils.assertNumEquals(srsi.getValue(20), 1);
        TestUtils.assertNumEquals(srsi.getValue(21), 0.9186);
        TestUtils.assertNumEquals(srsi.getValue(22), 0.9305);
        TestUtils.assertNumEquals(srsi.getValue(23), 1);
        TestUtils.assertNumEquals(srsi.getValue(24), 1);
    }
}

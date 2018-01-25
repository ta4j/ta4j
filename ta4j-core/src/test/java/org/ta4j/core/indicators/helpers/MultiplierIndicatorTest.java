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
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Num.Num;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;

import static org.ta4j.core.TATestsUtils.CURENCT_NUM_FUNCTION;

public class MultiplierIndicatorTest {
    private MultiplierIndicator multiplierIndicator;

    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<Num>(series, CURENCT_NUM_FUNCTION.apply(6));
        multiplierIndicator = new MultiplierIndicator(constantIndicator, 0.75);
    }

    @Test
    public void constantIndicator() {
        TATestsUtils.assertNumEquals(multiplierIndicator.getValue(10), "4.5");
        TATestsUtils.assertNumEquals(multiplierIndicator.getValue(1), "4.5");
        TATestsUtils.assertNumEquals(multiplierIndicator.getValue(0), "4.5");
        TATestsUtils.assertNumEquals(multiplierIndicator.getValue(30), "4.5");
    }
}

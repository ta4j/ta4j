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

public class SumIndicatorTest {

    private SumIndicator sumIndicator;
    
    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<>(series, series.valueOf(6));
        FixedIndicator<Num> mockIndicator = new FixedIndicator<Num>(series,
                series.valueOf(-2.0),
                series.valueOf(0.00),
                series.valueOf(1.00),
                series.valueOf(2.53),
                series.valueOf(5.87),
                series.valueOf(6.00),
                series.valueOf(10.0)
        );
        FixedIndicator<Num> mockIndicator2 = new FixedIndicator<Num>(series,
                series.valueOf(0),
                series.valueOf(1),
                series.valueOf(2),
                series.valueOf(3),
                series.valueOf(10),
                series.valueOf(-42),
                series.valueOf(-1337)
        );
        sumIndicator = new SumIndicator(constantIndicator, mockIndicator, mockIndicator2);
    }

    @Test
    public void getValue() {
        TATestsUtils.assertNumEquals(sumIndicator.getValue(0), "4.0");
        TATestsUtils.assertNumEquals(sumIndicator.getValue(1), "7.0");
        TATestsUtils.assertNumEquals(sumIndicator.getValue(2), "9.0");
        TATestsUtils.assertNumEquals(sumIndicator.getValue(3), "11.53");
        TATestsUtils.assertNumEquals(sumIndicator.getValue(4), "21.87");
        TATestsUtils.assertNumEquals(sumIndicator.getValue(5), "-30.0");
        TATestsUtils.assertNumEquals(sumIndicator.getValue(6), "-1321.0");
    }
}

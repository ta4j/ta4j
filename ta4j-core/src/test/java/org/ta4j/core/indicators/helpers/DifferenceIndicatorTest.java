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
import org.ta4j.core.Num.BigDecimalNum;
import org.ta4j.core.Num.Num;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;

import java.util.function.Function;

public class DifferenceIndicatorTest {

    private DifferenceIndicator differenceIndicator;
    
    @Before
    public void setUp() {
        Function<Number, Num> numFunction = BigDecimalNum::valueOf;

        TimeSeries series = new BaseTimeSeries();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<Num>(series,
                numFunction.apply(-2.0),
                numFunction.apply(0.00),
                numFunction.apply(1.00),
                numFunction.apply(2.53),
                numFunction.apply(5.87),
                numFunction.apply(6.00),
                numFunction.apply(10.0)
        );
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<Num>(series, numFunction.apply(6));
        differenceIndicator = new DifferenceIndicator(constantIndicator, mockIndicator);
    }

    @Test
    public void getValue() {
        TATestsUtils.assertNumEquals("8", differenceIndicator.getValue(0));
        TATestsUtils.assertNumEquals("6", differenceIndicator.getValue(1));
        TATestsUtils.assertNumEquals("5", differenceIndicator.getValue(2));
        TATestsUtils.assertNumEquals("3.47", differenceIndicator.getValue(3));
        TATestsUtils.assertNumEquals("0.13", differenceIndicator.getValue(4));
        TATestsUtils.assertNumEquals("0", differenceIndicator.getValue(5));
        TATestsUtils.assertNumEquals("-4", differenceIndicator.getValue(6));
    }
}

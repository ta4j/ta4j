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

import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Num.Num;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class AbsoluteIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public AbsoluteIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void constantIndicators() {
        TimeSeries series = new BaseTimeSeries();
        AbsoluteIndicator positiveInd = new AbsoluteIndicator(new ConstantIndicator<Num>(series, numFunction.apply(1337)));
        AbsoluteIndicator zeroInd = new AbsoluteIndicator(new ConstantIndicator<Num>(series, numFunction.apply(0)));
        AbsoluteIndicator negativeInd = new AbsoluteIndicator(new ConstantIndicator<Num>(series, numFunction.apply(-42.42)));
        for (int i = 0; i < 10; i++) {
            TestUtils.assertNumEquals(positiveInd.getValue(i), 1337);
            TestUtils.assertNumEquals(zeroInd.getValue(i), 0);
            assertNumEquals(negativeInd.getValue(i), 42.42);
        }
    }
}

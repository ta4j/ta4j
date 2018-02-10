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
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

public class GainIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeries data;

    public GainIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockTimeSeries(numFunction, 1, 2, 3, 4, 3, 4, 7, 4, 3, 3, 5, 3, 2);
    }

    @Test
    public void gainUsingClosePrice() {
        GainIndicator gain = new GainIndicator(new ClosePriceIndicator(data));
        TestUtils.assertNumEquals(gain.getValue(0), 0);
        TestUtils.assertNumEquals(gain.getValue(1), 1);
        TestUtils.assertNumEquals(gain.getValue(2), 1);
        TestUtils.assertNumEquals(gain.getValue(3), 1);
        TestUtils.assertNumEquals(gain.getValue(4), 0);
        TestUtils.assertNumEquals(gain.getValue(5), 1);
        TestUtils.assertNumEquals(gain.getValue(6), 3);
        TestUtils.assertNumEquals(gain.getValue(7), 0);
        TestUtils.assertNumEquals(gain.getValue(8), 0);
        TestUtils.assertNumEquals(gain.getValue(9), 0);
        TestUtils.assertNumEquals(gain.getValue(10), 2);
        TestUtils.assertNumEquals(gain.getValue(11), 0);
        TestUtils.assertNumEquals(gain.getValue(12), 0);
    }
}

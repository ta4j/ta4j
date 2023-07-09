/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.Num;

public class SumIndicatorTest {

    private SumIndicator sumIndicator;

    @Before
    public void setUp() {
        BarSeries series = new BaseBarSeries();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<>(series, series.numOf(6));
        FixedIndicator<Num> mockIndicator = new FixedIndicator<Num>(series, series.numOf(-2.0), series.numOf(0.00),
                series.numOf(1.00), series.numOf(2.53), series.numOf(5.87), series.numOf(6.00), series.numOf(10.0));
        FixedIndicator<Num> mockIndicator2 = new FixedIndicator<Num>(series, series.numOf(0), series.numOf(1),
                series.numOf(2), series.numOf(3), series.numOf(10), series.numOf(-42), series.numOf(-1337));
        sumIndicator = new SumIndicator(constantIndicator, mockIndicator, mockIndicator2);
    }

    @Test
    public void getValue() {
        assertNumEquals("4.0", sumIndicator.getValue(0));
        assertNumEquals("7.0", sumIndicator.getValue(1));
        assertNumEquals("9.0", sumIndicator.getValue(2));
        assertNumEquals("11.53", sumIndicator.getValue(3));
        assertNumEquals("21.87", sumIndicator.getValue(4));
        assertNumEquals("-30.0", sumIndicator.getValue(5));
        assertNumEquals("-1321.0", sumIndicator.getValue(6));
    }
}

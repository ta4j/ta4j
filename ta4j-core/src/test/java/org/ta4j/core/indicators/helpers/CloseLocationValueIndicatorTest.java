/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class CloseLocationValueIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CloseLocationValueIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(10, 18, 20, 10, numFunction));
        bars.add(new MockBar(17, 20, 21, 17, numFunction));
        bars.add(new MockBar(15, 15, 16, 14, numFunction));
        bars.add(new MockBar(15, 11, 15, 8, numFunction));
        bars.add(new MockBar(11, 12, 12, 10, numFunction));
        bars.add(new MockBar(10, 10, 10, 10, numFunction));
        bars.add(new MockBar(11, 12, 12, 10, numFunction));
        bars.add(new MockBar(11, 120, 140, 100, numFunction));
        series = new MockBarSeries(bars);
    }

    @Test
    public void getValue() {
        CloseLocationValueIndicator clv = new CloseLocationValueIndicator(series);
        assertNumEquals(0.6, clv.getValue(0));
        assertNumEquals(0.5, clv.getValue(1));
        assertNumEquals(0, clv.getValue(2));
        assertNumEquals(-1d / 7, clv.getValue(3));
        assertNumEquals(1, clv.getValue(4));
    }

    @Test
    public void returnZeroIfHighEqualsLow() {
        CloseLocationValueIndicator clv = new CloseLocationValueIndicator(series);
        assertNumEquals(0, clv.getValue(5));
        assertNumEquals(1, clv.getValue(6));
        assertNumEquals(0, clv.getValue(7));
    }
}

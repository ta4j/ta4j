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
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class PVOIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries barSeries;

    public PVOIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        bars.add(new MockBar(0, 10, numFunction));
        bars.add(new MockBar(0, 11, numFunction));
        bars.add(new MockBar(0, 12, numFunction));
        bars.add(new MockBar(0, 13, numFunction));
        bars.add(new MockBar(0, 150, numFunction));
        bars.add(new MockBar(0, 155, numFunction));
        bars.add(new MockBar(0, 160, numFunction));
        barSeries = new MockBarSeries(bars);
    }

    @Test
    public void createPvoIndicator() {
        PVOIndicator pvo = new PVOIndicator(barSeries);
        assertNumEquals(0.791855204, pvo.getValue(1));
        assertNumEquals(2.164434756, pvo.getValue(2));
        assertNumEquals(3.925400464, pvo.getValue(3));
        assertNumEquals(55.296120101, pvo.getValue(4));
        assertNumEquals(66.511722064, pvo.getValue(5));
    }
}

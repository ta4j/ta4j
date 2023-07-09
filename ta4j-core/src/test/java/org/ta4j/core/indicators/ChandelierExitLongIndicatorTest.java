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
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.Num;

public class ChandelierExitLongIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public ChandelierExitLongIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<Bar>();
        // open, close, high, low
        bars.add(new MockBar(44.98, 45.05, 45.17, 44.96, numFunction));
        bars.add(new MockBar(45.05, 45.10, 45.15, 44.99, numFunction));
        bars.add(new MockBar(45.11, 45.19, 45.32, 45.11, numFunction));
        bars.add(new MockBar(45.19, 45.14, 45.25, 45.04, numFunction));
        bars.add(new MockBar(45.12, 45.15, 45.20, 45.10, numFunction));
        bars.add(new MockBar(45.15, 45.14, 45.20, 45.10, numFunction));
        bars.add(new MockBar(45.13, 45.10, 45.16, 45.07, numFunction));
        bars.add(new MockBar(45.12, 45.15, 45.22, 45.10, numFunction));
        bars.add(new MockBar(45.15, 45.22, 45.27, 45.14, numFunction));
        bars.add(new MockBar(45.24, 45.43, 45.45, 45.20, numFunction));
        bars.add(new MockBar(45.43, 45.44, 45.50, 45.39, numFunction));
        bars.add(new MockBar(45.43, 45.55, 45.60, 45.35, numFunction));
        bars.add(new MockBar(45.58, 45.55, 45.61, 45.39, numFunction));
        bars.add(new MockBar(45.45, 45.01, 45.55, 44.80, numFunction));
        bars.add(new MockBar(45.03, 44.23, 45.04, 44.17, numFunction));

        data = new BaseBarSeries(bars);
    }

    @Test
    public void massIndexUsing3And8BarCounts() {
        ChandelierExitLongIndicator cel = new ChandelierExitLongIndicator(data, 5, 2);

        assertNumEquals(44.9853, cel.getValue(5));
        assertNumEquals(45.0162, cel.getValue(6));
        assertNumEquals(44.9590, cel.getValue(7));
        assertNumEquals(44.9852, cel.getValue(8));
        assertNumEquals(45.1221, cel.getValue(9));
        assertNumEquals(45.1937, cel.getValue(10));
        assertNumEquals(45.2549, cel.getValue(11));
        assertNumEquals(45.2459, cel.getValue(12));
        assertNumEquals(45.0187, cel.getValue(13));
        assertNumEquals(44.7890, cel.getValue(14));
    }
}

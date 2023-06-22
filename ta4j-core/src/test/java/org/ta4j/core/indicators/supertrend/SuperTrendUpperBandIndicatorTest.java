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
package org.ta4j.core.indicators.supertrend;

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
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class SuperTrendUpperBandIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries data;

    public SuperTrendUpperBandIndicatorTest(Function<Number, DoubleNum> numFunction) {
        super(DoubleNum::valueOf);
    }

    @Before
    public void setUp() {
        List<Bar> bars = new ArrayList<>();

        bars.add(new MockBar(23.17, 21.48, 23.39, 21.35, numFunction));
        bars.add(new MockBar(21.25, 19.94, 21.29, 20.07, numFunction));
        bars.add(new MockBar(20.08, 21.97, 24.30, 20.01, numFunction));
        bars.add(new MockBar(22.17, 20.87, 22.64, 20.78, numFunction));
        bars.add(new MockBar(21.67, 21.65, 22.80, 21.59, numFunction));
        bars.add(new MockBar(21.47, 22.14, 22.26, 20.96, numFunction));
        bars.add(new MockBar(22.25, 21.44, 22.31, 21.36, numFunction));
        bars.add(new MockBar(21.83, 21.67, 22.40, 21.59, numFunction));
        bars.add(new MockBar(23.09, 22.90, 23.76, 22.73, numFunction));
        bars.add(new MockBar(22.93, 22.01, 23.27, 21.94, numFunction));
        bars.add(new MockBar(19.89, 19.20, 20.47, 18.91, numFunction));
        bars.add(new MockBar(21.56, 18.83, 21.80, 18.83, numFunction));
        bars.add(new MockBar(19.00, 18.35, 19.41, 18.01, numFunction));
        bars.add(new MockBar(19.89, 6.36, 20.22, 6.21, numFunction));
        bars.add(new MockBar(19.28, 10.34, 20.58, 10.11, numFunction));

        data = new MockBarSeries(bars);
    }

    @Test
    public void testSuperTrendUpperBandIndicator() {
        SuperTrendUpperBandIndicator superTrendUpperBandIndicator = new SuperTrendUpperBandIndicator(data);

        assertNumEquals(this.numOf(26.610999999999997), superTrendUpperBandIndicator.getValue(1));
        assertNumEquals(this.numOf(26.610999999999997), superTrendUpperBandIndicator.getValue(6));
        assertNumEquals(this.numOf(24.67820648851259), superTrendUpperBandIndicator.getValue(12));
    }

}

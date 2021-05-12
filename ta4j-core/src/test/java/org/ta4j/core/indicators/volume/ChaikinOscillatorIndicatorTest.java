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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class ChaikinOscillatorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ChaikinOscillatorIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void getValue() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(12.915, 13.600, 12.890, 13.550, 264266, numFunction));
        bars.add(new MockBar(13.550, 13.770, 13.310, 13.505, 305427, numFunction));
        bars.add(new MockBar(13.510, 13.590, 13.425, 13.490, 104077, numFunction));
        bars.add(new MockBar(13.515, 13.545, 13.400, 13.480, 136135, numFunction));
        bars.add(new MockBar(13.490, 13.495, 13.310, 13.345, 92090, numFunction));
        bars.add(new MockBar(13.350, 13.490, 13.325, 13.420, 80948, numFunction));
        bars.add(new MockBar(13.415, 13.460, 13.290, 13.300, 82983, numFunction));
        bars.add(new MockBar(13.320, 13.320, 13.090, 13.130, 126918, numFunction));
        bars.add(new MockBar(13.145, 13.225, 13.090, 13.150, 68560, numFunction));
        bars.add(new MockBar(13.150, 13.250, 13.110, 13.245, 41178, numFunction));
        bars.add(new MockBar(13.245, 13.250, 13.120, 13.210, 63606, numFunction));
        bars.add(new MockBar(13.210, 13.275, 13.185, 13.275, 34402, numFunction));

        BarSeries series = new MockBarSeries(bars);
        ChaikinOscillatorIndicator co = new ChaikinOscillatorIndicator(series);

        assertNumEquals(0.0, co.getValue(0));
        assertNumEquals(-361315.15734265576, co.getValue(1));
        assertNumEquals(-611288.0465670675, co.getValue(2));
        assertNumEquals(-771681.707243684, co.getValue(3));
        assertNumEquals(-1047600.3223165069, co.getValue(4));
        assertNumEquals(-1128952.3867409695, co.getValue(5));
        assertNumEquals(-1930922.241574394, co.getValue(6));
        assertNumEquals(-2507483.932954022, co.getValue(7));
        assertNumEquals(-2591747.9037044123, co.getValue(8));
        assertNumEquals(-2404678.698472605, co.getValue(9));
        assertNumEquals(-2147771.081319658, co.getValue(10));
        assertNumEquals(-1858366.685091666, co.getValue(11));
    }
}

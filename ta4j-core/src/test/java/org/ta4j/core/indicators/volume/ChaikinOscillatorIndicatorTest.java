/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ChaikinOscillatorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ChaikinOscillatorIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder()
                .openPrice(12.915)
                .closePrice(13.600)
                .highPrice(12.890)
                .lowPrice(13.550)
                .volume(264266)
                .add();
        series.barBuilder()
                .openPrice(13.550)
                .closePrice(13.770)
                .highPrice(13.310)
                .lowPrice(13.505)
                .volume(305427)
                .add();
        series.barBuilder()
                .openPrice(13.510)
                .closePrice(13.590)
                .highPrice(13.425)
                .lowPrice(13.490)
                .volume(104077)
                .add();
        series.barBuilder()
                .openPrice(13.515)
                .closePrice(13.545)
                .highPrice(13.400)
                .lowPrice(13.480)
                .volume(136135)
                .add();
        series.barBuilder().openPrice(13.490).closePrice(13.495).highPrice(13.310).lowPrice(13.345).volume(92090).add();
        series.barBuilder().openPrice(13.350).closePrice(13.490).highPrice(13.325).lowPrice(13.420).volume(80948).add();
        series.barBuilder().openPrice(13.415).closePrice(13.460).highPrice(13.290).lowPrice(13.300).volume(82983).add();
        series.barBuilder()
                .openPrice(13.320)
                .closePrice(13.320)
                .highPrice(13.090)
                .lowPrice(13.130)
                .volume(126918)
                .add();
        series.barBuilder().openPrice(13.145).closePrice(13.225).highPrice(13.090).lowPrice(13.150).volume(68560).add();
        series.barBuilder().openPrice(13.150).closePrice(13.250).highPrice(13.110).lowPrice(13.245).volume(41178).add();
        series.barBuilder().openPrice(13.245).closePrice(13.250).highPrice(13.120).lowPrice(13.210).volume(63606).add();
        series.barBuilder().openPrice(13.210).closePrice(13.275).highPrice(13.185).lowPrice(13.275).volume(34402).add();

        var co = new ChaikinOscillatorIndicator(series);

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

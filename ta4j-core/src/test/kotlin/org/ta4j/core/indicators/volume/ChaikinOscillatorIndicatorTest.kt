/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.indicators.volume

import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class ChaikinOscillatorIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun getValue() {
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(12.915, 13.600, 12.890, 13.550, 264266.0, numFunction))
        bars.add(MockBar(13.550, 13.770, 13.310, 13.505, 305427.0, numFunction))
        bars.add(MockBar(13.510, 13.590, 13.425, 13.490, 104077.0, numFunction))
        bars.add(MockBar(13.515, 13.545, 13.400, 13.480, 136135.0, numFunction))
        bars.add(MockBar(13.490, 13.495, 13.310, 13.345, 92090.0, numFunction))
        bars.add(MockBar(13.350, 13.490, 13.325, 13.420, 80948.0, numFunction))
        bars.add(MockBar(13.415, 13.460, 13.290, 13.300, 82983.0, numFunction))
        bars.add(MockBar(13.320, 13.320, 13.090, 13.130, 126918.0, numFunction))
        bars.add(MockBar(13.145, 13.225, 13.090, 13.150, 68560.0, numFunction))
        bars.add(MockBar(13.150, 13.250, 13.110, 13.245, 41178.0, numFunction))
        bars.add(MockBar(13.245, 13.250, 13.120, 13.210, 63606.0, numFunction))
        bars.add(MockBar(13.210, 13.275, 13.185, 13.275, 34402.0, numFunction))
        val series: BarSeries = MockBarSeries(bars)
        val co = ChaikinOscillatorIndicator(series)
        TestUtils.assertNumEquals(0.0, co[0])
        TestUtils.assertNumEquals(-361315.15734265576, co.getValue(1))
        TestUtils.assertNumEquals(-611288.0465670675, co.getValue(2))
        TestUtils.assertNumEquals(-771681.707243684, co.getValue(3))
        TestUtils.assertNumEquals(-1047600.3223165069, co.getValue(4))
        TestUtils.assertNumEquals(-1128952.3867409695, co.getValue(5))
        TestUtils.assertNumEquals(-1930922.241574394, co.getValue(6))
        TestUtils.assertNumEquals(-2507483.932954022, co.getValue(7))
        TestUtils.assertNumEquals(-2591747.9037044123, co.getValue(8))
        TestUtils.assertNumEquals(-2404678.698472605, co.getValue(9))
        TestUtils.assertNumEquals(-2147771.081319658, co.getValue(10))
        TestUtils.assertNumEquals(-1858366.685091666, co.getValue(11))
    }
}
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
import java.time.ZonedDateTime
import java.util.function.Function

class IIIIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun intradayIntensityIndex() {
        val now = ZonedDateTime.now()
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(now, 0.0, 10.0, 12.0, 8.0, 0.0, 200.0, 0, numFunction)) // 2-2 * 200 / 4
        bars.add(MockBar(now, 0.0, 8.0, 10.0, 7.0, 0.0, 100.0, 0, numFunction)) // 1-2 *100 / 3
        bars.add(MockBar(now, 0.0, 9.0, 15.0, 6.0, 0.0, 300.0, 0, numFunction)) // 3-6 *300 /9
        bars.add(MockBar(now, 0.0, 20.0, 40.0, 5.0, 0.0, 50.0, 0, numFunction)) // 15-20 *50 / 35
        bars.add(MockBar(now, 0.0, 30.0, 30.0, 3.0, 0.0, 600.0, 0, numFunction)) // 27-0 *600 /27
        val series: BarSeries = MockBarSeries(bars)
        val iiiIndicator = IIIIndicator(series)
        TestUtils.assertNumEquals(0, iiiIndicator[0])
        TestUtils.assertNumEquals((2 * 8.0 - 10.0 - 7.0) / ((10.0 - 7.0) * 100.0), iiiIndicator.getValue(1))
        TestUtils.assertNumEquals((2 * 9.0 - 15.0 - 6.0) / ((15.0 - 6.0) * 300.0), iiiIndicator.getValue(2))
        TestUtils.assertNumEquals((2 * 20.0 - 40.0 - 5.0) / ((40.0 - 5.0) * 50.0), iiiIndicator.getValue(3))
        TestUtils.assertNumEquals((2 * 30.0 - 30.0 - 3.0) / ((30.0 - 3.0) * 600.0), iiiIndicator.getValue(4))
    }
}
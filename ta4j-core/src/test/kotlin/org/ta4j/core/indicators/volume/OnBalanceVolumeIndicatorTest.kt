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
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

class OnBalanceVolumeIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun getValue() {
        val now = ZonedDateTime.now()
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(now, 0.0, 10.0, 0.0, 0.0, 0.0, 4.0, 0, numFunction))
        bars.add(MockBar(now, 0.0, 5.0, 0.0, 0.0, 0.0, 2.0, 0, numFunction))
        bars.add(MockBar(now, 0.0, 6.0, 0.0, 0.0, 0.0, 3.0, 0, numFunction))
        bars.add(MockBar(now, 0.0, 7.0, 0.0, 0.0, 0.0, 8.0, 0, numFunction))
        bars.add(MockBar(now, 0.0, 7.0, 0.0, 0.0, 0.0, 6.0, 0, numFunction))
        bars.add(MockBar(now, 0.0, 6.0, 0.0, 0.0, 0.0, 10.0, 0, numFunction))
        val obv = OnBalanceVolumeIndicator(MockBarSeries(bars))
        TestUtils.assertNumEquals(0, obv[0])
        TestUtils.assertNumEquals(-2, obv.getValue(1))
        TestUtils.assertNumEquals(1, obv.getValue(2))
        TestUtils.assertNumEquals(9, obv.getValue(3))
        TestUtils.assertNumEquals(9, obv.getValue(4))
        TestUtils.assertNumEquals(-1, obv.getValue(5))
    }

    @Test
    fun stackOverflowError() {
        val bigListOfBars: MutableList<Bar> = ArrayList()
        for (i in 0..9999) {
            bigListOfBars.add(MockBar(i.toDouble(), numFunction))
        }
        val bigSeries = MockBarSeries(bigListOfBars)
        val obv = OnBalanceVolumeIndicator(bigSeries)
        // If a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        TestUtils.assertNumEquals(0, obv.getValue(9999))
    }
}
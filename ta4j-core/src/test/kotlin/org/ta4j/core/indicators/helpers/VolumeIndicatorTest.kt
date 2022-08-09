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
package org.ta4j.core.indicators.helpers

import junit.framework.TestCase
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

class VolumeIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun indicatorShouldRetrieveBarVolume() {
        val series: BarSeries = MockBarSeries(numFunction)
        val volumeIndicator = VolumeIndicator(series)
        for (i in 0..9) {
            TestCase.assertEquals(volumeIndicator[i], series.getBar(i).volume)
        }
    }

    @Test
    fun sumOfVolume() {
        val bars: MutableList<Bar> = ArrayList()
        bars.add(MockBar(0.0, 10.0, numFunction))
        bars.add(MockBar(0.0, 11.0, numFunction))
        bars.add(MockBar(0.0, 12.0, numFunction))
        bars.add(MockBar(0.0, 13.0, numFunction))
        bars.add(MockBar(0.0, 150.0, numFunction))
        bars.add(MockBar(0.0, 155.0, numFunction))
        bars.add(MockBar(0.0, 160.0, numFunction))
        val volumeIndicator = VolumeIndicator(MockBarSeries(bars), 3)
        TestUtils.assertNumEquals(10, volumeIndicator[0])
        TestUtils.assertNumEquals(21, volumeIndicator.getValue(1))
        TestUtils.assertNumEquals(33, volumeIndicator.getValue(2))
        TestUtils.assertNumEquals(36, volumeIndicator.getValue(3))
        TestUtils.assertNumEquals(175, volumeIndicator.getValue(4))
        TestUtils.assertNumEquals(318, volumeIndicator.getValue(5))
        TestUtils.assertNumEquals(465, volumeIndicator.getValue(6))
    }
}
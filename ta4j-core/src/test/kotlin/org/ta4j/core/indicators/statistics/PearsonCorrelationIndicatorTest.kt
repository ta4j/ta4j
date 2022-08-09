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
package org.ta4j.core.indicators.statistics

import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

class PearsonCorrelationIndicatorTest(function: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(function) {
    private var close: Indicator<Num>? = null
    private var volume: Indicator<Num>? = null
    @Before
    fun setUp() {
        val data: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        var i = 20
        // close, volume
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 6.0, 100.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 7.0, 105.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 9.0, 130.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 12.0, 160.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 11.0, 150.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 10.0, 130.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 11.0, 95.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 13.0, 120.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 15.0, 180.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 12.0, 160.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 8.0, 150.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 4.0, 200.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 3.0, 150.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 4.0, 85.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 3.0, 70.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 5.0, 90.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 8.0, 100.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 9.0, 95.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i--.toLong()), 11.0, 110.0, numFunction))
        data.addBar(MockBar(ZonedDateTime.now().minusSeconds(i.toLong()), 10.0, 95.0, numFunction))
        close = ClosePriceIndicator(data)
        volume = VolumeIndicator(data, 2)
    }

    @Test
    fun test() {
        val coef = PearsonCorrelationIndicator(close!!, volume!!, 5)
        TestUtils.assertNumEquals(0.94947469058476818628408908843839, coef.getValue(1))
        TestUtils.assertNumEquals(0.9640797490298872, coef.getValue(2))
        TestUtils.assertNumEquals(0.9666189661412724, coef.getValue(3))
        TestUtils.assertNumEquals(0.9219, coef.getValue(4))
        TestUtils.assertNumEquals(0.9205, coef.getValue(5))
        TestUtils.assertNumEquals(0.4565, coef.getValue(6))
        TestUtils.assertNumEquals(-0.4622, coef.getValue(7))
        TestUtils.assertNumEquals(0.05747, coef.getValue(8))
        TestUtils.assertNumEquals(0.1442, coef.getValue(9))
        TestUtils.assertNumEquals(-0.1263, coef.getValue(10))
        TestUtils.assertNumEquals(-0.5345, coef.getValue(11))
        TestUtils.assertNumEquals(-0.7275, coef.getValue(12))
        TestUtils.assertNumEquals(0.1676, coef.getValue(13))
        TestUtils.assertNumEquals(0.2506, coef.getValue(14))
        TestUtils.assertNumEquals(-0.2938, coef.getValue(15))
        TestUtils.assertNumEquals(-0.3586, coef.getValue(16))
        TestUtils.assertNumEquals(0.1713, coef.getValue(17))
        TestUtils.assertNumEquals(0.9841, coef.getValue(18))
        TestUtils.assertNumEquals(0.9799, coef.getValue(19))
    }
}
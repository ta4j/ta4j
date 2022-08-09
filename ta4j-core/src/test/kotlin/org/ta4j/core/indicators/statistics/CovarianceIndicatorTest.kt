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

class CovarianceIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
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
    fun usingBarCount5UsingClosePriceAndVolume() {
        val covar = CovarianceIndicator(close!!, volume!!, 5)
        TestUtils.assertNumEquals(0, covar[0])
        TestUtils.assertNumEquals(26.25, covar.getValue(1))
        TestUtils.assertNumEquals(63.3333, covar.getValue(2))
        TestUtils.assertNumEquals(143.75, covar.getValue(3))
        TestUtils.assertNumEquals(156, covar.getValue(4))
        TestUtils.assertNumEquals(60.8, covar.getValue(5))
        TestUtils.assertNumEquals(15.2, covar.getValue(6))
        TestUtils.assertNumEquals(-17.6, covar.getValue(7))
        TestUtils.assertNumEquals(4, covar.getValue(8))
        TestUtils.assertNumEquals(11.6, covar.getValue(9))
        TestUtils.assertNumEquals(-14.4, covar.getValue(10))
        TestUtils.assertNumEquals(-100.2, covar.getValue(11))
        TestUtils.assertNumEquals(-70.0, covar.getValue(12))
        TestUtils.assertNumEquals(24.6, covar.getValue(13))
        TestUtils.assertNumEquals(35.0, covar.getValue(14))
        TestUtils.assertNumEquals(-19.0, covar.getValue(15))
        TestUtils.assertNumEquals(-47.8, covar.getValue(16))
        TestUtils.assertNumEquals(11.4, covar.getValue(17))
        TestUtils.assertNumEquals(55.8, covar.getValue(18))
        TestUtils.assertNumEquals(33.4, covar.getValue(19))
    }

    @Test
    fun firstValueShouldBeZero() {
        val covar = CovarianceIndicator(close!!, volume!!, 5)
        TestUtils.assertNumEquals(0, covar[0])
    }

    @Test
    fun shouldBeZeroWhenBarCountIs1() {
        val covar = CovarianceIndicator(close!!, volume!!, 1)
        TestUtils.assertNumEquals(0, covar.getValue(3))
        TestUtils.assertNumEquals(0, covar.getValue(8))
    }
}
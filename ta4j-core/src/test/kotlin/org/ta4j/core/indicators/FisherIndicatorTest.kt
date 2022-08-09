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
package org.ta4j.core.indicators

import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

class FisherIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(null, numFunction) {
    protected var series: BarSeries? = null
    @Before
    fun setUp() {
        series = BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("NaN test").build()
        var i = 20
        // open, close, max, min
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                44.98,
                45.05,
                45.17,
                44.96,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.05,
                45.10,
                45.15,
                44.99,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.11,
                45.19,
                45.32,
                45.11,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.19,
                45.14,
                45.25,
                45.04,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.12,
                45.15,
                45.20,
                45.10,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.15,
                45.14,
                45.20,
                45.10,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.13,
                45.10,
                45.16,
                45.07,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.12,
                45.15,
                45.22,
                45.10,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.15,
                45.22,
                45.27,
                45.14,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.24,
                45.43,
                45.45,
                45.20,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.43,
                45.44,
                45.50,
                45.39,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.43,
                45.55,
                45.60,
                45.35,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.58,
                45.55,
                45.61,
                45.39,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.45,
                45.01,
                45.55,
                44.80,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                45.03,
                44.23,
                45.04,
                44.17,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                44.23,
                43.95,
                44.29,
                43.81,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                43.91,
                43.08,
                43.99,
                43.08,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                43.07,
                43.55,
                43.65,
                43.06,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(
                ZonedDateTime.now().minusSeconds(i--.toLong()),
                43.56,
                43.95,
                43.99,
                43.53,
                0.0,
                1.0,
                0,
                numFunction
            )
        )
        series!!.addBar(
            MockBar(ZonedDateTime.now().minusSeconds(i.toLong()), 43.93, 44.47, 44.58, 43.93, 0.0, 1.0, 0, numFunction)
        )
    }

    @Test
    fun fisher() {
        val fisher = FisherIndicator(series)
        TestUtils.assertNumEquals(0.6448642008177138, fisher.getValue(10))
        TestUtils.assertNumEquals(0.8361770425706673, fisher.getValue(11))
        TestUtils.assertNumEquals(0.9936697984965788, fisher.getValue(12))
        TestUtils.assertNumEquals(0.8324807235379169, fisher.getValue(13))
        TestUtils.assertNumEquals(0.5026313552592737, fisher.getValue(14))
        TestUtils.assertNumEquals(0.06492516204615063, fisher.getValue(15))
    }
}
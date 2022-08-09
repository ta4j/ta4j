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
package org.ta4j.core.indicators.bollinger

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class PercentBIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var closePrice: ClosePriceIndicator? = null
    @Before
    fun setUp() {
        val data: BarSeries = MockBarSeries(
            numFunction,
            10.0,
            12.0,
            15.0,
            14.0,
            17.0,
            20.0,
            21.0,
            20.0,
            20.0,
            19.0,
            20.0,
            17.0,
            12.0,
            12.0,
            9.0,
            8.0,
            9.0,
            10.0,
            9.0,
            10.0
        )
        closePrice = ClosePriceIndicator(data)
    }

    @Test
    fun percentBUsingSMAAndStandardDeviation() {
        val pcb = PercentBIndicator(closePrice!!, 5, 2.0)
        Assert.assertTrue(pcb[0].isNaN)
        TestUtils.assertNumEquals(0.75, pcb.getValue(1))
        TestUtils.assertNumEquals(0.8244, pcb.getValue(2))
        TestUtils.assertNumEquals(0.6627, pcb.getValue(3))
        TestUtils.assertNumEquals(0.8517, pcb.getValue(4))
        TestUtils.assertNumEquals(0.90328, pcb.getValue(5))
        TestUtils.assertNumEquals(0.83, pcb.getValue(6))
        TestUtils.assertNumEquals(0.6552, pcb.getValue(7))
        TestUtils.assertNumEquals(0.5737, pcb.getValue(8))
        TestUtils.assertNumEquals(0.1047, pcb.getValue(9))
        TestUtils.assertNumEquals(0.5, pcb.getValue(10))
        TestUtils.assertNumEquals(0.0284, pcb.getValue(11))
        TestUtils.assertNumEquals(0.0344, pcb.getValue(12))
        TestUtils.assertNumEquals(0.2064, pcb.getValue(13))
        TestUtils.assertNumEquals(0.1835, pcb.getValue(14))
        TestUtils.assertNumEquals(0.2131, pcb.getValue(15))
        TestUtils.assertNumEquals(0.3506, pcb.getValue(16))
        TestUtils.assertNumEquals(0.5737, pcb.getValue(17))
        TestUtils.assertNumEquals(0.5, pcb.getValue(18))
        TestUtils.assertNumEquals(0.7673, pcb.getValue(19))
    }
}
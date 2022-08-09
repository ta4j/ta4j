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
package org.ta4j.core.rules

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator
import org.ta4j.core.num.Num

class UnderIndicatorRuleTest {
    private var rule: UnderIndicatorRule? = null
    @Before
    fun setUp() {
        val series: BarSeries = BaseBarSeries()
        val indicator: Indicator<Num> = FixedDecimalIndicator(series, 0.0, 5.0, 8.0, 5.0, 1.0, 10.0, 20.0, 30.0)
        rule = UnderIndicatorRule(indicator, series.numOf(5))
    }

    @Test
    fun isSatisfied() {
        Assert.assertTrue(rule!!.isSatisfied(0))
        Assert.assertFalse(rule!!.isSatisfied(1))
        Assert.assertFalse(rule!!.isSatisfied(2))
        Assert.assertFalse(rule!!.isSatisfied(3))
        Assert.assertTrue(rule!!.isSatisfied(4))
        Assert.assertFalse(rule!!.isSatisfied(5))
        Assert.assertFalse(rule!!.isSatisfied(6))
        Assert.assertFalse(rule!!.isSatisfied(7))
    }
}
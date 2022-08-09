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

class InSlopeRuleTest {
    private var rulePositiveSlope: InSlopeRule? = null
    private var ruleNegativeSlope: InSlopeRule? = null
    @Before
    fun setUp() {
        val series: BarSeries = BaseBarSeries()
        val indicator: Indicator<Num> =
            FixedDecimalIndicator(series, 50.0, 70.0, 80.0, 90.0, 99.0, 60.0, 30.0, 20.0, 10.0, 0.0)
        rulePositiveSlope = InSlopeRule(indicator, series.numOf(20), series.numOf(30))
        ruleNegativeSlope = InSlopeRule(indicator, series.numOf(-40), series.numOf(-20))
    }

    @Test
    fun isSatisfied() {
            Assert.assertFalse(rulePositiveSlope!!.isSatisfied(0))
            Assert.assertTrue(rulePositiveSlope!!.isSatisfied(1))
            Assert.assertFalse(rulePositiveSlope!!.isSatisfied(2))
            Assert.assertFalse(rulePositiveSlope!!.isSatisfied(9))
            Assert.assertFalse(ruleNegativeSlope!!.isSatisfied(0))
            Assert.assertFalse(ruleNegativeSlope!!.isSatisfied(1))
            Assert.assertTrue(ruleNegativeSlope!!.isSatisfied(5))
            Assert.assertFalse(ruleNegativeSlope!!.isSatisfied(9))
        }
}
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

import org.apache.commons.math3.stat.regression.SimpleRegression
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class SimpleLinearRegressionIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var closePrice: Indicator<Num>? = null
    @Before
    fun setUp() {
        val data = doubleArrayOf(10.0, 20.0, 30.0, 40.0, 30.0, 40.0, 30.0, 20.0, 30.0, 50.0, 60.0, 70.0, 80.0)
        closePrice = ClosePriceIndicator(MockBarSeries(numFunction, *data))
    }

    @Test
    fun notComputedLinearRegression() {
        var linearReg = SimpleLinearRegressionIndicator(closePrice!!, 0)
        Assert.assertTrue(linearReg[0].isNaN)
        Assert.assertTrue(linearReg.getValue(1).isNaN)
        Assert.assertTrue(linearReg.getValue(2).isNaN)
        linearReg = SimpleLinearRegressionIndicator(closePrice!!, 1)
        Assert.assertTrue(linearReg[0].isNaN)
        Assert.assertTrue(linearReg.getValue(1).isNaN)
        Assert.assertTrue(linearReg.getValue(2).isNaN)
    }

    @Test
    fun calculateLinearRegressionWithLessThan2ObservationsReturnsNaN() {
        var reg = SimpleLinearRegressionIndicator(closePrice!!, 0)
        Assert.assertTrue(reg[0].isNaN)
        Assert.assertTrue(reg.getValue(3).isNaN)
        Assert.assertTrue(reg.getValue(6).isNaN)
        Assert.assertTrue(reg.getValue(9).isNaN)
        reg = SimpleLinearRegressionIndicator(closePrice!!, 1)
        Assert.assertTrue(reg[0].isNaN)
        Assert.assertTrue(reg.getValue(3).isNaN)
        Assert.assertTrue(reg.getValue(6).isNaN)
        Assert.assertTrue(reg.getValue(9).isNaN)
    }

    @Test
    fun calculateLinearRegressionOn4Observations() {
        val reg = SimpleLinearRegressionIndicator(closePrice!!, 4)
        TestUtils.assertNumEquals(20, reg.getValue(1))
        TestUtils.assertNumEquals(30, reg.getValue(2))
        var origReg = buildSimpleRegression(10.0, 20.0, 30.0, 40.0)
        TestUtils.assertNumEquals(40, reg.getValue(3))
        TestUtils.assertNumEquals(origReg.predict(3.0), reg.getValue(3))
        origReg = buildSimpleRegression(30.0, 40.0, 30.0, 40.0)
        TestUtils.assertNumEquals(origReg.predict(3.0), reg.getValue(5))
        origReg = buildSimpleRegression(30.0, 20.0, 30.0, 50.0)
        TestUtils.assertNumEquals(origReg.predict(3.0), reg.getValue(9))
    }

    @Test
    fun calculateLinearRegression() {
        val values = doubleArrayOf(1.0, 2.0, 1.3, 3.75, 2.25)
        val indicator = ClosePriceIndicator(MockBarSeries(numFunction, *values))
        val reg = SimpleLinearRegressionIndicator(indicator, 5)
        val origReg = buildSimpleRegression(*values)
        TestUtils.assertNumEquals(origReg.predict(4.0), reg.getValue(4))
    }

    companion object {
        /**
         * @param values values
         * @return a simple linear regression based on provided values
         */
        private fun buildSimpleRegression(vararg values: Double): SimpleRegression {
            val simpleReg = SimpleRegression()
            for (i in values.indices) {
                simpleReg.addData(i.toDouble(), values[i])
            }
            return simpleReg
        }
    }
}
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

import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.abs
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.divide
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.log
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.max
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.min
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.minus
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.multiply
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.plus
import org.ta4j.core.indicators.helpers.TransformIndicator.Companion.sqrt
import org.ta4j.core.num.Num
import java.util.function.Function

class TransformIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var transPlus: TransformIndicator? = null
    private var transMinus: TransformIndicator? = null
    private var transMultiply: TransformIndicator? = null
    private var transDivide: TransformIndicator? = null
    private var transMax: TransformIndicator? = null
    private var transMin: TransformIndicator? = null
    private var transAbs: TransformIndicator? = null
    private var transSqrt: TransformIndicator? = null
    private var transLog: TransformIndicator? = null
    @Before
    fun setUp() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        val constantIndicator = ConstantIndicator(series, numOf(4))
        transPlus = plus(constantIndicator, 10)
        transMinus = minus(constantIndicator, 10)
        transMultiply = multiply(constantIndicator, 10)
        transDivide = divide(constantIndicator, 10)
        transMax = max(constantIndicator, 10)
        transMin = min(constantIndicator, 10)
        transAbs = abs(ConstantIndicator<Num>(series, numOf(-4)))
        transSqrt = sqrt(constantIndicator)
        transLog = log(constantIndicator)
    }

    @Test
    fun getValue() {
        TestUtils.assertNumEquals(14, transPlus!![0])
        TestUtils.assertNumEquals(-6, transMinus!![0])
        TestUtils.assertNumEquals(40, transMultiply!![0])
        TestUtils.assertNumEquals(0.4, transDivide!![0])
        TestUtils.assertNumEquals(10, transMax!![0])
        TestUtils.assertNumEquals(4, transMin!![0])
        TestUtils.assertNumEquals(4, transAbs!![0])
        TestUtils.assertNumEquals(2, transSqrt!![0])
        TestUtils.assertNumEquals(1.3862943611198906, transLog!![0])
    }
}
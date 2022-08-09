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
import org.ta4j.core.*
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.CombineIndicator.Companion.divide
import org.ta4j.core.indicators.helpers.CombineIndicator.Companion.max
import org.ta4j.core.indicators.helpers.CombineIndicator.Companion.min
import org.ta4j.core.indicators.helpers.CombineIndicator.Companion.minus
import org.ta4j.core.indicators.helpers.CombineIndicator.Companion.multiply
import org.ta4j.core.indicators.helpers.CombineIndicator.Companion.plus
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.Num
import java.util.function.Function

class CombineIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var combinePlus: CombineIndicator? = null
    private var combineMinus: CombineIndicator? = null
    private var combineMultiply: CombineIndicator? = null
    private var combineDivide: CombineIndicator? = null
    private var combineMax: CombineIndicator? = null
    private var combineMin: CombineIndicator? = null
    @Before
    fun setUp() {
        val series: BarSeries = BaseBarSeriesBuilder().withNumTypeOf(numFunction).build()
        val constantIndicator = ConstantIndicator(series, numOf(4))
        val constantIndicatorTwo = ConstantIndicator(series, numOf(2))
        combinePlus = plus(constantIndicator, constantIndicatorTwo)
        combineMinus = minus(constantIndicator, constantIndicatorTwo)
        combineMultiply = multiply(constantIndicator, constantIndicatorTwo)
        combineDivide = divide(constantIndicator, constantIndicatorTwo)
        combineMax = max(constantIndicator, constantIndicatorTwo)
        combineMin = min(constantIndicator, constantIndicatorTwo)
    }

    @Test
    fun testAllDefaultMathCombineFunctions() {
        TestUtils.assertNumEquals(6, combinePlus!![0])
        TestUtils.assertNumEquals(2, combineMinus!![0])
        TestUtils.assertNumEquals(8, combineMultiply!![0])
        TestUtils.assertNumEquals(2, combineDivide!![0])
        TestUtils.assertNumEquals(4, combineMax!![0])
        TestUtils.assertNumEquals(2, combineMin!![0])
    }

    @Test
    fun testDifferenceIndicator() {
        val numFunction = Function<Number, Num> { obj: Number? -> DecimalNum.Companion.valueOf(obj) }
        val series: BarSeries = BaseBarSeries()
        val mockIndicator = FixedIndicator(
            series, numFunction.apply(-2.0),
            numFunction.apply(0.00), numFunction.apply(1.00), numFunction.apply(2.53), numFunction.apply(5.87),
            numFunction.apply(6.00), numFunction.apply(10.0)
        )
        val constantIndicator = ConstantIndicator(series, numFunction.apply(6))
        val differenceIndicator = minus(constantIndicator, mockIndicator)
        TestUtils.assertNumEquals("8", differenceIndicator[0])
        TestUtils.assertNumEquals("6", differenceIndicator.getValue(1))
        TestUtils.assertNumEquals("5", differenceIndicator.getValue(2))
        TestUtils.assertNumEquals("3.47", differenceIndicator.getValue(3))
        TestUtils.assertNumEquals("0.13", differenceIndicator.getValue(4))
        TestUtils.assertNumEquals("0", differenceIndicator.getValue(5))
        TestUtils.assertNumEquals("-4", differenceIndicator.getValue(6))
    }
}
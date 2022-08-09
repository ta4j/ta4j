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
package org.ta4j.core.criteria

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.ta4j.core.AnalysisCriterion
import org.ta4j.core.CriterionFactory
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import java.util.*
import java.util.function.Function

@RunWith(Parameterized::class)
abstract class AbstractCriterionTest
/**
 * Constructor.
 *
 * @param factory CriterionFactory for building an AnalysisCriterion given
 * parameters
 */(private val factory: CriterionFactory, protected val numFunction: Function<Number?, Num>) {
    protected val openedPositionUtils = OpenedPositionUtils()

    /**
     * Generates an AnalysisCriterion given criterion parameters.
     *
     * @param params criterion parameters
     * @return AnalysisCriterion given parameters
     */
    fun getCriterion(vararg params: Any?): AnalysisCriterion? {
        return factory.getCriterion(*params)
    }

    fun numOf(n: Number?): Num? {
        return numFunction.apply(n)
    }

    companion object {
        @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=DecimalNum)")
        @JvmStatic
        fun function(): List<Function<Number, Num>> {
            return listOf(
                Function { obj: Number? -> DoubleNum.valueOf(obj) },
                Function { obj: Number? -> DecimalNum.valueOf(obj) })
        }
    }
}
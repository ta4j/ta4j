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
package org.ta4j.core.indicators.numeric

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.num.*
import java.util.function.BinaryOperator

/**
 * Objects of this class defer evaluation of an arithmetic operation.
 *
 * This is a lightweight version of the CombineIndicator; it doesn't cache.
 */
internal class BinaryOperation private constructor(
    private val operator: BinaryOperator<Num>,
    private val left: Indicator<Num>,
    private val right: Indicator<Num>?
) : Indicator<Num> {
    override fun getValue(index: Int): Num {
        val n1 = left[index]
        val n2 = right!![index]
        return operator.apply(n1, n2)
    }

    override val barSeries: BarSeries?
        get() = left.barSeries

    override fun numOf(number: Number): Num {
        return left.numOf(number)
    }

    companion object {
        @JvmStatic
        fun sum(left: Indicator<Num>, right: Indicator<Num>?): BinaryOperation {
            return BinaryOperation({ obj: Num?, augend: Num? -> obj!!.plus(augend!!) }, left, right)
        }

        @JvmStatic
        fun difference(left: Indicator<Num>, right: Indicator<Num>?): BinaryOperation {
            return BinaryOperation({ obj: Num?, subtrahend: Num? -> obj!!.minus(subtrahend!!) }, left, right)
        }

        @JvmStatic
        fun product(left: Indicator<Num>, right: Indicator<Num>?): BinaryOperation {
            return BinaryOperation({ obj: Num?, multiplicand: Num? -> obj!!.times(multiplicand!!) }, left, right)
        }

        @JvmStatic
        fun quotient(left: Indicator<Num>, right: Indicator<Num>?): BinaryOperation {
            return BinaryOperation({ obj: Num?, divisor: Num? -> obj!!.div(divisor!!) }, left, right)
        }

        @JvmStatic
        fun min(left: Indicator<Num>, right: Indicator<Num>): BinaryOperation {
            return BinaryOperation({ obj: Num?, other: Num? -> obj!!.min(other!!) }, left, right)
        }

        @JvmStatic
        fun max(left: Indicator<Num>, right: Indicator<Num>?): BinaryOperation {
            return BinaryOperation({ obj: Num?, other: Num? -> obj!!.max(other!!) }, left, right)
        }
    }
}
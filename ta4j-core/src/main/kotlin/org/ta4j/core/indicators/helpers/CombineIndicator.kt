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

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.*
import java.util.function.BinaryOperator

/**
 * Combine indicator.
 *
 *
 * Combines two Num indicators by using common math operations.
 *
 */
class CombineIndicator// TODO check both indicators use the same series/num function
    /**
* Constructor.
*
* @param indicatorLeft the indicator for the left hand side of the calculation
* @param indicatorRight the indicator for the right hand side of the
* calculation
* @param combination a [Function] describing the combination function
* to combine the values of the indicators
*/ constructor (
private val indicatorLeft: Indicator<Num>, private
val indicatorRight: Indicator<Num>,
private val combineFunction: BinaryOperator<Num>
) : CachedIndicator<Num>(indicatorLeft){
    override fun calculate(index: Int): Num {
        return combineFunction.apply(indicatorLeft[index], indicatorRight[index])
    }

    override fun toString(): String {
        return javaClass.getSimpleName()
    }
    companion object {
        /**
         * Combines the two input indicators by indicatorLeft.plus(indicatorRight).
         */
        @JvmStatic
        fun plus(indicatorLeft: Indicator<Num>, indicatorRight: Indicator<Num>): CombineIndicator {
            return CombineIndicator(
                indicatorLeft,
                indicatorRight,
                BinaryOperator { obj: Num?, augend: Num? -> obj!!.plus(augend!!) })
        }

        /**
         * Combines the two input indicators by indicatorLeft.minus(indicatorRight).
         */
        @JvmStatic
        fun minus(indicatorLeft: Indicator<Num>, indicatorRight: Indicator<Num>): CombineIndicator {
            return CombineIndicator(
                indicatorLeft,
                indicatorRight,
                BinaryOperator { obj: Num?, subtrahend: Num? -> obj!!.minus(subtrahend!!) })
        }

        /**
         * Combines the two input indicators by indicatorLeft.div(indicatorRight).
         */
        @JvmStatic
        fun divide(indicatorLeft: Indicator<Num>, indicatorRight: Indicator<Num>): CombineIndicator {
            return CombineIndicator(
                indicatorLeft,
                indicatorRight,
                BinaryOperator { obj: Num?, divisor: Num? -> obj!!.div(divisor!!) })
        }

        /**
         * Combines the two input indicators by
         * indicatorLeft.times(indicatorRight).
         */
        @JvmStatic
        fun multiply(indicatorLeft: Indicator<Num>, indicatorRight: Indicator<Num>): CombineIndicator {
            return CombineIndicator(
                indicatorLeft,
                indicatorRight,
                BinaryOperator { obj: Num?, multiplicand: Num? -> obj!!.times(multiplicand!!) })
        }

        /**
         * Combines the two input indicators by indicatorLeft.max(indicatorRight).
         */
        @JvmStatic
        fun max(indicatorLeft: Indicator<Num>, indicatorRight: Indicator<Num>): CombineIndicator {
            return CombineIndicator(
                indicatorLeft,
                indicatorRight,
                BinaryOperator { obj: Num?, other: Num? -> obj!!.max(other!!) })
        }

        /**
         * Combines the two input indicators by indicatorLeft.min(indicatorRight).
         */
        @JvmStatic
        fun min(indicatorLeft: Indicator<Num>, indicatorRight: Indicator<Num>): CombineIndicator {
            return CombineIndicator(
                indicatorLeft,
                indicatorRight,
                BinaryOperator { obj: Num?, other: Num? -> obj!!.min(other!!) })
        }
    }
}
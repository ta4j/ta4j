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
import java.util.function.UnaryOperator

/**
 * Transform indicator.
 *
 *
 * Transforms the Num of any indicator by using common math operations.
 *
 * @apiNote Minimal deviations in last decimal places possible. During some
 * calculations this indicator converts [DecimalNum][Num] to
 * [double][Double]
 */
class TransformIndicator
/**
 * Constructor.
 *
 * @param indicator      the indicator
 * @param transformation a [Function] describing the transformation
 */(private val indicator: Indicator<Num>, private val transformationFunction: UnaryOperator<Num>) :
    CachedIndicator<Num>(
        indicator
    ) {
    override fun calculate(index: Int): Num {
        return transformationFunction.apply(indicator[index])
    }

    override fun toString(): String {
        return javaClass.simpleName
    }

    companion object {
        /**
         * Transforms the input indicator by indicator.plus(coefficient).
         */
        @JvmStatic
        fun plus(indicator: Indicator<Num>, coefficient: Number): TransformIndicator {
            val numCoefficient = indicator.numOf(coefficient)
            return TransformIndicator(indicator) { `val`: Num? -> `val`!!.plus(numCoefficient) }
        }

        /**
         * Transforms the input indicator by indicator.minus(coefficient).
         */
        @JvmStatic
        fun minus(indicator: Indicator<Num>, coefficient: Number): TransformIndicator {
            val numCoefficient = indicator.numOf(coefficient)
            return TransformIndicator(indicator) { `val`: Num? -> `val`!!.minus(numCoefficient) }
        }

        /**
         * Transforms the input indicator by indicator.div(coefficient).
         */
        @JvmStatic
        fun divide(indicator: Indicator<Num>, coefficient: Number): TransformIndicator {
            val numCoefficient = indicator.numOf(coefficient)
            return TransformIndicator(indicator) { `val`: Num? -> `val`!!.div(numCoefficient) }
        }

        /**
         * Transforms the input indicator by indicator.times(coefficient).
         */
        @JvmStatic
        fun multiply(indicator: Indicator<Num>, coefficient: Number): TransformIndicator {
            val numCoefficient = indicator.numOf(coefficient)
            return TransformIndicator(indicator) { `val`: Num? -> `val`!!.times(numCoefficient) }
        }

        /**
         * Transforms the input indicator by indicator.max(coefficient).
         */
        @JvmStatic
        fun max(indicator: Indicator<Num>, coefficient: Number): TransformIndicator {
            val numCoefficient = indicator.numOf(coefficient)
            return TransformIndicator(indicator) { `val`: Num? -> `val`!!.max(numCoefficient) }
        }

        /**
         * Transforms the input indicator by indicator.min(coefficient).
         */
        @JvmStatic
        fun min(indicator: Indicator<Num>, coefficient: Number): TransformIndicator {
            val numCoefficient = indicator.numOf(coefficient)
            return TransformIndicator(indicator) { `val`: Num? -> `val`!!.min(numCoefficient) }
        }

        /**
         * Transforms the input indicator by indicator.abs().
         */
        @JvmStatic
        fun abs(indicator: Indicator<Num>): TransformIndicator {
            return TransformIndicator(indicator) { obj: Num? -> obj!!.abs() }
        }

        /**
         * Transforms the input indicator by indicator.sqrt().
         */
        @JvmStatic
        fun sqrt(indicator: Indicator<Num>): TransformIndicator {
            return TransformIndicator(indicator) { obj: Num? -> obj!!.sqrt() }
        }

        /**
         * Transforms the input indicator by indicator.log().
         *
         * @apiNote precision may be lost, because this implementation is using the
         * underlying doubleValue method
         */
        @JvmStatic
        fun log(indicator: Indicator<Num>): TransformIndicator {
            return TransformIndicator(indicator) { `val`: Num? ->
                `val`!!.numOf(
                    Math.log(
                        `val`.doubleValue()
                    )
                )
            }
        }
    }
}
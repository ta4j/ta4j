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
package org.ta4j.core

import org.ta4j.core.num.*
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.math.max

/**
 * Indicator over a [bar series][BarSeries].
 *

p> For each index of the
* bar series, returns a value of type **T**.
*
* @param <T > the type of returned value (Double, Boolean, etc.)
</T> */
@JvmDefaultWithCompatibility
interface Indicator<T> {
    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    fun getValue(index: Int): T

    operator fun get(index:Int ):T=getValue(index)

    /**
     * @return the related bar series
     */
    val barSeries: BarSeries?

    /**
     * @return the [Num extending class][Num] for the given [Number]
     */
    fun numOf(number: Number): Num

    fun stream(): Stream<T> {
        return IntStream.range(barSeries!!.beginIndex, barSeries!!.endIndex + 1)
            .mapToObj { index: Int -> this[index] }
    }

    companion object {
        /**
         * Returns all values from an [Indicator] as an array of Doubles. The
         * returned doubles could have a minor loss of precise, if [Indicator] was
         * based on [Num].
         *
         * @param ref      the indicator
         * @param index    the index
         * @param barCount the barCount
         * @return array of Doubles within the barCount
         */
        @JvmStatic
        fun toDouble(ref: Indicator<Num>, index: Int, barCount: Int): Array<Double> {
            val startIndex = max(0, index - barCount + 1)
            return IntStream.range(startIndex, startIndex + barCount)
                .mapToObj { id: Int -> ref.getValue(id) }
                .map { obj: Num -> obj.doubleValue() }
                .toArray {
                    fdd(it)
                }
        }

        private fun fdd(it: Int): Array<out Double> {
            return Array(it){it.toDouble()}
        }
    }
}
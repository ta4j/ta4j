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
package org.ta4j.core.indicators

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import kotlin.math.max

/**
 * Recursive cached [indicator][Indicator].
 *
 * Recursive indicators should extend this class.<br></br>
 * This class is only here to avoid (OK, to postpone) the StackOverflowError
 * that may be thrown on the first getValue(int) call of a recursive indicator.
 * Concretely when an index value is asked, if the last cached value is too
 * old/far, the computation of all the values between the last cached and the
 * asked one is executed iteratively.
 */
abstract class RecursiveCachedIndicator<T>
/**
 * Constructor.
 *
 * @param series the related bar series
 */
protected constructor(series: BarSeries?) : CachedIndicator<T>(series) {
    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series)
     */
    protected constructor(indicator: Indicator<*>) : this(indicator.barSeries)

    override fun getValue(index: Int): T {
        val series = barSeries
        if (series != null) {
            val seriesEndIndex = series.endIndex
            if (index <= seriesEndIndex) {
                // We are not after the end of the series
                val removedBarsCount = series.removedBarsCount
                val startIndex = max(removedBarsCount, highestResultIndex)
                if (index - startIndex > RECURSION_THRESHOLD) {
                    // Too many uncalculated values; the risk for a StackOverflowError becomes high.
                    // Calculating the previous values iteratively
                    for (prevIdx in startIndex until index) {
                        super.getValue(prevIdx)
                    }
                }
            }
        }
        return super.getValue(index)
    }

    companion object {
        /**
         * The recursion threshold for which an iterative calculation is executed. TODO
         * Should be variable (depending on the sub-indicators used in this indicator)
         */
        private const val RECURSION_THRESHOLD = 100
    }
}
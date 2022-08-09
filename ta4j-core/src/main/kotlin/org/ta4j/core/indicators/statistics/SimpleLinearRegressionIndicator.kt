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

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * Simple linear regression indicator.
 *
 * A moving (i.e. over the time frame) simple linear regression (least squares).
 * y = slope * x + intercept See also:
 * http://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html
 */
/**
 * Constructor.
 *
 * @param indicator the indicator for the x-values of the formula.
 * @param barCount  the time frame
 * @param type      the type of the outcome value (y, slope, intercept)
 */

class SimpleLinearRegressionIndicator
@JvmOverloads constructor(
    private val indicator: Indicator<Num>,
    private val barCount: Int,
    private val type: SimpleLinearRegressionType = SimpleLinearRegressionType.Y
) : CachedIndicator<Num>(
    indicator
) {
    /**
     * The type for the outcome of the [SimpleLinearRegressionIndicator]
     */
    enum class SimpleLinearRegressionType {
        Y, SLOPE, INTERCEPT
    }

    private var slope: Num? = null
    private var intercept: Num? = null

    override fun calculate(index: Int): Num {
        val startIndex = max(0, index - barCount + 1)
        if (index - startIndex + 1 < 2) {
            // Not enough observations to compute a regression line
            return NaN.NaN
        }
        calculateRegressionLine(startIndex, index)
        if (type == SimpleLinearRegressionType.SLOPE) {
            return slope!!
        }
        return if (type == SimpleLinearRegressionType.INTERCEPT) {
            intercept!!
        } else (slope!! * numOf(index)) + intercept!!
    }

    /**
     * Calculates the regression line.
     *
     * @param startIndex the start index (inclusive) in the bar series
     * @param endIndex   the end index (inclusive) in the bar series
     */
    private fun calculateRegressionLine(startIndex: Int, endIndex: Int) {
        // First pass: compute xBar and yBar
        var sumX = numOf(0)
        var sumY = numOf(0)
        for (i in startIndex..endIndex) {
            sumX += numOf(i)
            sumY += indicator[i]
        }
        val nbObservations = numOf(endIndex - startIndex + 1)
        val xBar = sumX / nbObservations
        val yBar = sumY / nbObservations

        // Second pass: compute slope and intercept
        var xxBar = numOf(0)
        var xyBar = numOf(0)
        for (i in startIndex..endIndex) {
            val dX = numOf(i) - xBar
            val dY = indicator[i] - yBar
            xxBar += dX * dX
            xyBar += dX * dY
        }
        slope = xyBar.div(xxBar)
        intercept = yBar.minus(slope!!.times(xBar))
    }
}
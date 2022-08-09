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
import org.ta4j.core.indicators.RecursiveCachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * Indicator-Pearson-Correlation
 *
 * @see [
 * http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/](http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/)
 */
/**
 * Constructor.
 *
 * @param indicator1 the first indicator
 * @param indicator2 the second indicator
 * @param barCount   the time frame
 */
class PearsonCorrelationIndicator
(
    private val indicator1: Indicator<Num>,
    private val indicator2: Indicator<Num>,
    private val barCount: Int) :
    RecursiveCachedIndicator<Num>(indicator1) {

    override fun calculate(index: Int): Num {
        val n = numOf(barCount)
        var Sx = numOf(0)
        var Sy = numOf(0)
        var Sxx = numOf(0)
        var Syy = numOf(0)
        var Sxy = numOf(0)
        for (i in max(barSeries!!.beginIndex, index - barCount + 1)..index) {
            val x = indicator1[i]
            val y = indicator2[i]
            Sx += x
            Sy += y
            Sxy += x*y
            Sxx += x*x
            Syy += y*y
        }

        // (n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy)
        val toSqrt = (n*Sxx - Sx*Sx)* (n*Syy - Sy*Sy)
        return if (toSqrt.isGreaterThan(numOf(0))) {
            // pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy))
            (n*Sxy - Sx*Sy)/(toSqrt.sqrt())
        } else NaN.NaN
    }
}
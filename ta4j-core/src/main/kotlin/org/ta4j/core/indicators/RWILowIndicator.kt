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
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * The Class RandomWalkIndexLowIndicator.
 *
 * @see [Source
 * of formular](http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html)
 */
class RWILowIndicator
/**
 * Constructor.
 *
 * @param series   the series
 * @param barCount the time frame
 */(series: BarSeries?, private val barCount: Int) : CachedIndicator<Num>(series) {
    override fun calculate(index: Int): Num {
        if (index - barCount + 1 < barSeries!!.beginIndex) {
            return NaN.NaN
        }
        var minRWIL = numOf(0)
        for (n in 2..barCount) {
            minRWIL = minRWIL.max(calcRWIHFor(index, n))
        }
        return minRWIL
    }

    private fun calcRWIHFor(index: Int, n: Int): Num {
        val series = barSeries
        val low = series!!.getBar(index).lowPrice!!
        val highN = series.getBar(index + 1 - n).highPrice
        val atrN = ATRIndicator(series, n)[index]
        val sqrtN = numOf(n).sqrt()
        return highN!!.minus(low).div(atrN.times(sqrtN))
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
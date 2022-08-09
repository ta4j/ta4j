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
package org.ta4j.core.indicators.aroon

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.helpers.LowPriceIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * Aroon down indicator.
 *
 * @see [chart_school:technical_indicators:aroon](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon)
 */
/**
 * Constructor.
 *
 * @param lowPriceIndicator the indicator for the low price (default
 * [LowPriceIndicator])
 * @param barCount          the time frame
 */

class AroonDownIndicator(private val lowPriceIndicator: Indicator<Num>, private val barCount: Int) :
    CachedIndicator<Num>(
        lowPriceIndicator
    ) {
    private val lowestLowPriceIndicator: LowestValueIndicator = LowestValueIndicator(lowPriceIndicator, barCount + 1)
    private val hundred: Num = numOf(100)
    private val barCountNum: Num = numOf(barCount)


    /**
     * Default Constructor that is using the low price
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    constructor(series: BarSeries?, barCount: Int) : this(LowPriceIndicator(series), barCount)

    override fun calculate(index: Int): Num {
        if (barSeries!!.getBar(index).lowPrice!!.isNaN) return NaN.NaN

        // Getting the number of bars since the lowest close price
        val endIndex = max(0, index - barCount)
        var nbBars = 0
        for (i in index downTo endIndex + 1) {
            if (lowPriceIndicator[i].isEqual(lowestLowPriceIndicator[index])) {
                break
            }
            nbBars++
        }
        return (numOf(barCount - nbBars) / barCountNum) * hundred
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
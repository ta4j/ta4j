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
package org.ta4j.core.indicators.ichimoku

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * Ichimoku clouds: Senkou Span B (Leading Span B) indicator
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud)
 */
class IchimokuSenkouSpanBIndicator @JvmOverloads constructor(series: BarSeries?, barCount: Int = 52, offset: Int = 26) :
    CachedIndicator<Num>(series) {
    // ichimoku avg line indicator
    var lineIndicator: IchimokuLineIndicator

    /**
     * Displacement on the chart (usually 26)
     */
    private val offset: Int
    /**
     * Constructor.
     *
     * @param series   the series
     * @param barCount the time frame (usually 52)
     * @param offset   displacement on the chart
     */
    /**
     * Constructor.
     *
     * @param series the series
     */
    /**
     * Constructor.
     *
     * @param series   the series
     * @param barCount the time frame (usually 52)
     */
    init {
        lineIndicator = IchimokuLineIndicator(series, barCount)
        this.offset = offset
    }

    override fun calculate(index: Int): Num {
        val spanIndex = index - offset + 1
        return if (spanIndex >= barSeries!!.beginIndex) {
            lineIndicator.getValue(spanIndex)
        } else {
            NaN.Companion.NaN
        }
    }
}
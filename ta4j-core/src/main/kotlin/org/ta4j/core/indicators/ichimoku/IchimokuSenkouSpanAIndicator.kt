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
 * Ichimoku clouds: Senkou Span A (Leading Span A) indicator
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud)
 */
/**
 * Constructor.
 *
 * @param series         the series
 * @param conversionLine the conversion line
 * @param baseLine       the base line
 * @param offset         kumo cloud displacement (offset) forward in time
 */

class IchimokuSenkouSpanAIndicator
 @JvmOverloads constructor(
    series: BarSeries?,
    /** The Tenkan-sen indicator  */
    private val conversionLine: IchimokuTenkanSenIndicator = IchimokuTenkanSenIndicator(series),
    /** The Kijun-sen indicator  */
    private val baseLine: IchimokuKijunSenIndicator = IchimokuKijunSenIndicator(series), // Cloud offset
    private val offset: Int = 26
) : CachedIndicator<Num>(series) {
    /**
     * Constructor.
     *
     * @param series                 the series
     * @param barCountConversionLine the time frame for the conversion line (usually* 9)
     * @param barCountBaseLine       the time frame for the base line (usually 26)
     */
    constructor(series: BarSeries?, barCountConversionLine: Int, barCountBaseLine: Int) : this(
        series, IchimokuTenkanSenIndicator(series, barCountConversionLine),
        IchimokuKijunSenIndicator(series, barCountBaseLine), 26
    )

    override fun calculate(index: Int): Num {

        // at index=7 we need index=3 when offset=5
        val spanIndex = index - offset + 1
        return if (spanIndex >= barSeries!!.beginIndex) {
            (conversionLine[spanIndex] + baseLine[spanIndex]) / numOf(2)
        } else {
            NaN.NaN
        }
    }
}
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
package org.ta4j.core.indicators.volume

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * The volume-weighted average price (VWAP) Indicator.
 *
 * @see [
 * http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp](http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp)
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday)
 *
 * @see [
 * https://en.wikipedia.org/wiki/Volume-weighted_average_price](https://en.wikipedia.org/wiki/Volume-weighted_average_price)
 */
class VWAPIndicator(series: BarSeries?, private val barCount: Int) : CachedIndicator<Num>(series) {
    private val typicalPrice: Indicator<Num>
    private val volume: Indicator<Num>
    private val zero: Num

    /**
     * Constructor.
     *
     * @param series   the series
     * @param barCount the time frame
     */
    init {
        typicalPrice = TypicalPriceIndicator(series)
        volume = VolumeIndicator(series)
        zero = numOf(0)
    }

    override fun calculate(index: Int): Num {
        if (index <= 0) {
            return typicalPrice[index]
        }
        val startIndex = max(0, index - barCount + 1)
        var cumulativeTPV = zero
        var cumulativeVolume = zero
        for (i in startIndex..index) {
            val currentVolume = volume[i]
            cumulativeTPV = cumulativeTPV.plus(typicalPrice[i].times(currentVolume))
            cumulativeVolume = cumulativeVolume.plus(currentVolume)
        }
        return cumulativeTPV.div(cumulativeVolume)
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
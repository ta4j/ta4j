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
package org.ta4j.core.indicators.candles

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.*

/**
 * Bearish Harami pattern indicator.
 *
 * @see [
 * http://www.investopedia.com/terms/b/bearishharami.asp](http://www.investopedia.com/terms/b/bearishharami.asp)
 */
/**
 * Constructor.
 *
 * @param series a bar series
 */

class BearishHaramiIndicator
    (series: BarSeries?) : CachedIndicator<Boolean>(series) {
    override fun calculate(index: Int): Boolean {
        if (index < 1) {
            // Harami is a 2-candle pattern
            return false
        }
        val prevBar = barSeries!!.getBar(index - 1)
        val currBar = barSeries.getBar(index)
        if (prevBar.isBullish && currBar.isBearish) {
            val prevOpenPrice = prevBar.openPrice
            val prevClosePrice = prevBar.closePrice
            val currOpenPrice = currBar.openPrice
            val currClosePrice = currBar.closePrice
            return (currOpenPrice!!.isGreaterThan(prevOpenPrice) && currOpenPrice.isLessThan(prevClosePrice)
                    && currClosePrice!!.isGreaterThan(prevOpenPrice) && currClosePrice.isLessThan(prevClosePrice))
        }
        return false
    }
}
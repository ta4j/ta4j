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
 * Upper shadow height indicator.
 *
 * Provides the (absolute) difference between the high price and the highest
 * price of the candle body. I.e.: high price - max(open price, close price)
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks.formation](http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks.formation)
 */
class UpperShadowIndicator
/**
 * Constructor.
 *
 * @param series the bar series
 */
    (series: BarSeries?) : CachedIndicator<Num>(series) {
    override fun calculate(index: Int): Num {
        val t = barSeries!!.getBar(index)
        val openPrice = t.openPrice!!
        val closePrice = t.closePrice!!
        return if (closePrice.isGreaterThan(openPrice)) {
            // Bullish
            t.highPrice!!.minus(closePrice)
        } else {
            // Bearish
            t.highPrice!!.minus(openPrice)
        }
    }
}
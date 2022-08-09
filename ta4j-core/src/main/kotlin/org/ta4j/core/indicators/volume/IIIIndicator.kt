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
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.HighPriceIndicator
import org.ta4j.core.indicators.helpers.LowPriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.num.*

/**
 * Intraday Intensity Index
 *
 * @see [https://www.investopedia.com/terms/i/intradayintensityindex.asp](https://www.investopedia.com/terms/i/intradayintensityindex.asp)
 */
class IIIIndicator(series: BarSeries) : CachedIndicator<Num>(series) {
    private val closePriceIndicator= ClosePriceIndicator(series)
    private val highPriceIndicator= HighPriceIndicator(series)
    private val lowPriceIndicator= LowPriceIndicator(series)
    private val volumeIndicator= VolumeIndicator(series)
    private val two: Num=numOf(2)


    override fun calculate(index: Int): Num {
        if (index == barSeries!!.beginIndex) {
            return numOf(0)
        }
        val doubledClosePrice = two.times(closePriceIndicator[index])
        val high = highPriceIndicator[index]
        val low = lowPriceIndicator[index]
        val highMinusLow = high - low
        val highPlusLow = high + low
        return (doubledClosePrice - highPlusLow)
            .div(highMinusLow.times(volumeIndicator[index]))
    }
}
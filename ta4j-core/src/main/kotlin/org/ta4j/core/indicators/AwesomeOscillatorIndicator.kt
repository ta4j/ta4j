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
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.helpers.MedianPriceIndicator
import org.ta4j.core.num.*

/**
 * Awesome oscillator. (AO)
 *
 * see https://www.tradingview.com/wiki/Awesome_Oscillator_(AO)
 */
/**
 * Constructor.
 *
 * @param indicator    (normally [MedianPriceIndicator])
 * @param barCountSma1 (normally 5)
 * @param barCountSma2 (normally 34)
 */

class AwesomeOscillatorIndicator @JvmOverloads constructor(
    indicator: Indicator<Num>,
    barCountSma1: Int = 5,
    barCountSma2: Int = 34
) : CachedIndicator<Num>(indicator) {
       private val sma5 = SMAIndicator(indicator, barCountSma1)
       private val sma34 = SMAIndicator(indicator, barCountSma2)

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    constructor(series: BarSeries?) : this(MedianPriceIndicator(series), 5, 34) {}

    override fun calculate(index: Int): Num {
        return sma5[index] - sma34[index]
    }
}
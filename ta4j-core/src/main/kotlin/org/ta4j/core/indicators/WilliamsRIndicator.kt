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
import org.ta4j.core.indicators.helpers.*
import org.ta4j.core.num.*

/**
 * William's R indicator.
 *
 *
 * @see [https://www.investopedia.com/terms/w/williamsr.asp](https://www.investopedia.com/terms/w/williamsr.asp)
 */
class WilliamsRIndicator(
    private val closePriceIndicator: ClosePriceIndicator,
    private val barCount: Int,
    highPriceIndicator: HighPriceIndicator,
    lowPriceIndicator: LowPriceIndicator
) : CachedIndicator<Num>(closePriceIndicator) {

    private val multiplier: Num = numOf(-100)

    constructor(barSeries: BarSeries?, barCount: Int) : this(
        ClosePriceIndicator(barSeries), barCount, HighPriceIndicator(barSeries),
        LowPriceIndicator(barSeries)
    )

    private val highestHigh = HighestValueIndicator(highPriceIndicator, barCount)
    private val lowestMin = LowestValueIndicator(lowPriceIndicator, barCount)

    override fun calculate(index: Int): Num {
        val highestHighPrice = highestHigh[index]
        val lowestLowPrice = lowestMin[index]
        return ((highestHighPrice - closePriceIndicator[index]) / (highestHighPrice - lowestLowPrice)) * multiplier
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
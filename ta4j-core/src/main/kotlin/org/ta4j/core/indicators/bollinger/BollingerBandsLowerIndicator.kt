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
package org.ta4j.core.indicators.bollinger

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.*

/**
 * Buy - Occurs when the price line crosses from below to above the Lower
 * Bollinger Band. Sell - Occurs when the price line crosses from above to below
 * the Upper Bollinger Band.
 *
 */
/**
 * Constructor. Defaults k value to 2.
 *
 * @param bbm       the middle band Indicator. Typically an SMAIndicator is used.
 * @param indicator the deviation above and below the middle, factored by k.
 * Typically a StandardDeviationIndicator is used.
 * @param k         the scaling factor to multiply the deviation by. Typically
 */
class BollingerBandsLowerIndicator
 @JvmOverloads constructor(
    private val bbm: BollingerBandsMiddleIndicator,
    private val indicator: Indicator<Num>,
    val k: Num = bbm.barSeries!!.numOf(2)
) : CachedIndicator<Num>(
    indicator
) {
    override fun calculate(index: Int): Num {
        return bbm[index].minus(indicator[index].times(k))
    }

    override fun toString(): String {
        return javaClass.simpleName + "k: " + k + "deviation: " + indicator + "series: " + bbm
    }
}

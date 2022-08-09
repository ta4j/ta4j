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

import org.ta4j.core.Indicator
import org.ta4j.core.num.*

/**
 * Double exponential moving average indicator.
 *
 *
 * @see [
 * https://en.wikipedia.org/wiki/Double_exponential_moving_average](https://en.wikipedia.org/wiki/Double_exponential_moving_average)
 */
/**
 * Constructor.
 *
 * @param indicator the indicator
 * @param barCount  the time frame
 */

class DoubleEMAIndicator(indicator: Indicator<Num>, private val barCount: Int) : CachedIndicator<Num>(indicator) {
    private val ema = EMAIndicator(indicator, barCount)
    private val emaEma = EMAIndicator(ema, barCount)

    override fun calculate(index: Int): Num {
        return ema[index] * numOf(2) - emaEma[index]
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
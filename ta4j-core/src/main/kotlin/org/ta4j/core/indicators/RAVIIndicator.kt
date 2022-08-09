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
 * Chande's Range Action Verification Index (RAVI) indicator.
 *
 * To preserve trend direction, default calculation does not use absolute value.
 */
/**
 * Constructor.
 *
 * @param price            the price
 * @param shortSmaBarCount the time frame for the short SMA (usually 7)
 * @param longSmaBarCount  the time frame for the long SMA (usually 65)
 */
class RAVIIndicator(price: Indicator<Num>, shortSmaBarCount: Int, longSmaBarCount: Int) : CachedIndicator<Num>(price) {
    private val shortSma: SMAIndicator
    private val longSma: SMAIndicator

    init {
        shortSma = SMAIndicator(price, shortSmaBarCount)
        longSma = SMAIndicator(price, longSmaBarCount)
    }

    override fun calculate(index: Int): Num {
        val shortMA = shortSma[index]
        val longMA = longSma[index]
        return ((shortMA - longMA) / longMA) * numOf(100)
    }
}
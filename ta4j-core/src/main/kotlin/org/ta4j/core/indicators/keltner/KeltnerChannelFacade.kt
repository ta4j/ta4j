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
package org.ta4j.core.indicators.keltner

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.ATRIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.numeric.NumericIndicator

/**
 * A facade to create the 3 Keltner Channel indicators. An exponential moving
 * average of close price is used as the middle channel.
 *
 *
 *
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. Overall there
 * is less caching and probably better performance.
 */
class KeltnerChannelFacade(bs: BarSeries?, emaCount: Int, atrCount: Int, k: Number) {
    private val middle: NumericIndicator
    private val upper: NumericIndicator
    private val lower: NumericIndicator

    init {
        val price: NumericIndicator = NumericIndicator.Companion.of(ClosePriceIndicator(bs))
        val atr: NumericIndicator = NumericIndicator.Companion.of(ATRIndicator(bs, atrCount))
        middle = price.ema(emaCount)
        upper = middle.plus(atr.times(k))
        lower = middle.minus(atr.times(k))
    }

    fun middle(): NumericIndicator {
        return middle
    }

    fun upper(): NumericIndicator {
        return upper
    }

    fun lower(): NumericIndicator {
        return lower
    }
}
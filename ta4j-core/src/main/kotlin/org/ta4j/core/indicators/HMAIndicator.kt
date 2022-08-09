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
import org.ta4j.core.indicators.helpers.CombineIndicator
import org.ta4j.core.indicators.helpers.TransformIndicator
import org.ta4j.core.num.*

/**
 * Hull moving average (HMA) indicator.
 *
 * @see [
 * http://alanhull.com/hull-moving-average](http://alanhull.com/hull-moving-average)
 */
class HMAIndicator(indicator: Indicator<Num>, private val barCount: Int) : CachedIndicator<Num>(indicator) {
    private val sqrtWma: WMAIndicator

    init {
        val halfWma = WMAIndicator(indicator, barCount / 2)
        val origWma = WMAIndicator(indicator, barCount)
        val indicatorForSqrtWma: Indicator<Num> =
            CombineIndicator.minus(TransformIndicator.multiply(halfWma, 2), origWma)
        sqrtWma = WMAIndicator(indicatorForSqrtWma, numOf(barCount).sqrt().intValue())
    }

    override fun calculate(index: Int): Num {
        return sqrtWma[index]
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}



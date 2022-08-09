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

import org.ta4j.core.indicators.ATRIndicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.*

/**
 * Keltner Channel (lower line) indicator
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels)
 */
class KeltnerChannelLowerIndicator(
    private val keltnerMiddleIndicator: KeltnerChannelMiddleIndicator,
    private val averageTrueRangeIndicator: ATRIndicator,
    ratio: Double
) : CachedIndicator<Num>(
    keltnerMiddleIndicator.barSeries
) {
    private val ratio: Num?

    constructor(middle: KeltnerChannelMiddleIndicator, ratio: Double, barCountATR: Int) : this(
        middle,
        ATRIndicator(middle.barSeries, barCountATR),
        ratio
    ) {
    }

    init {
        this.ratio = numOf(ratio)
    }

    override fun calculate(index: Int): Num {
        return keltnerMiddleIndicator[index]
            .minus(ratio!!.times(averageTrueRangeIndicator[index]))
    }

    val barCount: Int
        get() = keltnerMiddleIndicator.barCount

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
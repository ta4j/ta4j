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
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator
import org.ta4j.core.num.*

/**
 * Keltner Channel (middle line) indicator
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels)
 */
class KeltnerChannelMiddleIndicator(indicator: Indicator<Num>, barCountEMA: Int) :
    AbstractIndicator<Num>(indicator.barSeries) {
    private val emaIndicator: EMAIndicator

    constructor(series: BarSeries?, barCountEMA: Int) : this(TypicalPriceIndicator(series), barCountEMA) {}

    init {
        emaIndicator = EMAIndicator(indicator, barCountEMA)
    }

    override fun getValue(index: Int): Num {
        return emaIndicator[index]
    }

    val barCount: Int
        get() = emaIndicator.barCount

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
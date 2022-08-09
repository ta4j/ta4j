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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.CombineIndicator
import org.ta4j.core.indicators.helpers.PreviousValueIndicator
import org.ta4j.core.num.*

/**
 * The Detrended Price Oscillator (DPO) indicator.
 *
 * The Detrended Price Oscillator (DPO) is an indicator designed to remove trend
 * from price and make it easier to identify cycles. DPO does not extend to the
 * last date because it is based on a displaced moving average. However,
 * alignment with the most recent is not an issue because DPO is not a momentum
 * oscillator. Instead, DPO is used to identify cycles highs/lows and estimate
 * cycle length.
 *
 * In short, DPO(20) equals price 11 days ago less the 20-day SMA.
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci)
 */
class DPOIndicator(price: Indicator<Num>, barCount: Int) : CachedIndicator<Num>(price) {
    private val indicatorMinusPreviousSMAIndicator: CombineIndicator
    private val name: String

    /**
     * Constructor.
     *
     * @param series   the series
     * @param barCount the time frame
     */
    constructor(series: BarSeries?, barCount: Int) : this(ClosePriceIndicator(series), barCount) {}

    /**
     * Constructor.
     *
     * @param price    the price
     * @param barCount the time frame
     */
    init {
        val timeFrame = barCount / 2 + 1
        val simpleMovingAverage = SMAIndicator(price, barCount)
        val previousSimpleMovingAverage = PreviousValueIndicator(
            simpleMovingAverage,
            timeFrame
        )
        indicatorMinusPreviousSMAIndicator = CombineIndicator.Companion.minus(price, previousSimpleMovingAverage)
        name = String.format("%s barCount: %s", javaClass.simpleName, barCount)
    }

    override fun calculate(index: Int): Num {
        return indicatorMinusPreviousSMAIndicator[index]
    }

    override fun toString(): String {
        return name
    }
}
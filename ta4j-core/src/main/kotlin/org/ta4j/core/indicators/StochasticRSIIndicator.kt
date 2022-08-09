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
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.num.*

/**
 * The Stochastic RSI Indicator.
 *
 * Stoch RSI = (RSI - MinimumRSIn) / (MaximumRSIn - MinimumRSIn)
 */
/**
 * Constructor.
 *
 * @param rsi the rsi indicator
 * @param barCount     the time frame
 */

class StochasticRSIIndicator(private val rsi: RSIIndicator, barCount: Int) : CachedIndicator<Num>(
    rsi
) {
    private val minRsi = LowestValueIndicator(rsi, barCount)
    private val maxRsi = HighestValueIndicator(rsi, barCount)

    /**
     * Constructor. In most cases, this should be used to avoid confusion over what
     * Indicator parameters should be used.
     *
     * @param series   the series
     * @param barCount the time frame
     */
    constructor(series: BarSeries?, barCount: Int) : this(ClosePriceIndicator(series), barCount)

    /**
     * Constructor.
     *
     * @param indicator the Indicator, in practice is always a ClosePriceIndicator.
     * @param barCount  the time frame
     */
    constructor(indicator: Indicator<Num>, barCount: Int) : this(RSIIndicator(indicator, barCount), barCount) {}


    override fun calculate(index: Int): Num {
        val minRsiValue = minRsi[index]
        return (rsi[index] - minRsiValue) / (maxRsi[index] - minRsiValue)
    }
}
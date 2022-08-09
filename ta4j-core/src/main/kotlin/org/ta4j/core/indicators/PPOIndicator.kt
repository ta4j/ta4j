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
 * Percentage price oscillator (PPO) indicator. <br></br>
 * Aka. MACD Percentage Price Oscillator (MACD-PPO).
 *
 * @see [https://www.investopedia.com/terms/p/ppo.asp](https://www.investopedia.com/terms/p/ppo.asp)
 */
/**
 * Constructor.
 *
 * @param indicator     the indicator
 * @param shortBarCount the short time frame
 * @param longBarCount  the long time frame
 */

open class PPOIndicator @JvmOverloads constructor(
    indicator: Indicator<Num>,
    shortBarCount: Int = 12,
    longBarCount: Int = 26
) : CachedIndicator<Num>(indicator) {

    private val shortTermEma: EMAIndicator
    private val longTermEma: EMAIndicator

    init {
        require(shortBarCount <= longBarCount) { "Long term period count must be greater than short term period count" }
        shortTermEma = EMAIndicator(indicator, shortBarCount)
        longTermEma = EMAIndicator(indicator, longBarCount)
    }

    override fun calculate(index: Int): Num {
        val shortEmaValue = shortTermEma[index]
        val longEmaValue = longTermEma[index]
        return ((shortEmaValue - longEmaValue) / longEmaValue) * numOf(100)
    }
}
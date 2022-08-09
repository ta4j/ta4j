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
import org.ta4j.core.indicators.helpers.GainIndicator
import org.ta4j.core.indicators.helpers.LossIndicator
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * Chande Momentum Oscillator indicator.
 *
 * @see [
 * http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/](http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/)
 *
 * @see [
 * href="http://www.investopedia.com/terms/c/chandemomentumoscillator.asp"](http://www.investopedia.com/terms/c/chandemomentumoscillator.asp)
 */
class CMOIndicator(indicator: Indicator<Num>, barCount: Int) : CachedIndicator<Num>(indicator) {
    private val gainIndicator: GainIndicator
    private val lossIndicator: LossIndicator
    private val barCount: Int

    /**
     * Constructor.
     *
     * @param indicator a price indicator
     * @param barCount  the time frame
     */
    init {
        gainIndicator = GainIndicator(indicator)
        lossIndicator = LossIndicator(indicator)
        this.barCount = barCount
    }

    override fun calculate(index: Int): Num {
        var sumOfGains = numOf(0)
        for (i in max(1, index - barCount + 1)..index) {
            sumOfGains = sumOfGains.plus(gainIndicator[i])
        }
        var sumOfLosses = numOf(0)
        for (i in max(1, index - barCount + 1)..index) {
            sumOfLosses = sumOfLosses.plus(lossIndicator[i])
        }
        return sumOfGains.minus(sumOfLosses).div(sumOfGains.plus(sumOfLosses)).times(numOf(100))
    }
}
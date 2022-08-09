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

/**
 * Relative strength index indicator.
 *
 * Computed using original Welles Wilder formula.
 */
class RSIIndicator(indicator: Indicator<Num>, barCount: Int) : CachedIndicator<Num>(indicator) {
    private val averageGainIndicator: MMAIndicator
    private val averageLossIndicator: MMAIndicator

    init {
        averageGainIndicator = MMAIndicator(GainIndicator(indicator), barCount)
        averageLossIndicator = MMAIndicator(LossIndicator(indicator), barCount)
    }

    override fun calculate(index: Int): Num {
        // compute relative strength
        val averageGain = averageGainIndicator[index]
        val averageLoss = averageLossIndicator[index]
        if (averageLoss.isZero) {
            return if (averageGain.isZero) {
                numOf(0)
            } else {
                numOf(100)
            }
        }
        val relativeStrength = averageGain.div(averageLoss)
        // compute relative strength index
        return numOf(100).minus(numOf(100).div(numOf(1).plus(relativeStrength)))
    }
}
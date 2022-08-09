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
package org.ta4j.core.indicators.helpers

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * Difference Change Indicator.
 *
 * Get the difference in percentage from the last time the threshold was
 * reached.
 *
 * Or if you don't pass the threshold you will always just get the difference
 * percentage from the precious value.
 *
 */
class DifferencePercentageIndicator @JvmOverloads constructor(
    private val indicator: Indicator<Num>,
    private val percentageThreshold: Num = indicator.numOf(0)
) : CachedIndicator<Num>(
    indicator
) {
    private val hundred: Num = numOf(100)
    private var lastNotification: Num? = null

    constructor(indicator: Indicator<Num>, percentageThreshold: Number) : this(
        indicator,
        indicator.numOf(percentageThreshold)
    )
    override fun calculate(index: Int): Num {
        val value = indicator[index]
        if (lastNotification == null) {
            lastNotification = value
            return NaN.NaN
        }
        val changeFraction = value.div(lastNotification!!)
        val changePercentage = fractionToPercentage(changeFraction)
        if (changePercentage.abs().isGreaterThanOrEqual(percentageThreshold)) {
            lastNotification = value
        }
        return changePercentage
    }

    private fun fractionToPercentage(changeFraction: Num?): Num {
        return changeFraction!!.times(hundred).minus(hundred)
    }
}
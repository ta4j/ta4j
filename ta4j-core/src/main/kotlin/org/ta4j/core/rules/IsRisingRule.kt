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
package org.ta4j.core.rules

import org.ta4j.core.Indicator
import org.ta4j.core.TradingRecord
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * Indicator-rising-indicator rule.
 *
 * Satisfied when the values of the [indicator][Indicator] increase within
 * the barCount.
 */
class IsRisingRule
/**
 * Constructor for strict rising.
 *
 * @param ref      the indicator
 * @param barCount the time frame
 */ @JvmOverloads constructor(
    /** The actual indicator  */
    private val ref: Indicator<Num>,
    /** The barCount  */
    private val barCount: Int,
    /** The minimum required strenght of the rising  */
    private var minStrenght: Double = 1.0
) : AbstractRule() {
    /**
     * Constructor.
     *
     * @param ref         the indicator
     * @param barCount    the time frame
     * @param minStrenght the minimum required rising strenght (between '0' and '1',
     * e.g. '1' for strict rising)
     */
    /** This rule does not use the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        if (minStrenght >= 1) {
            minStrenght = 0.99
        }
        var count = 0
        for (i in max(0, index - barCount + 1)..index) {
            if (ref[i].isGreaterThan(ref.getValue(max(0, i - 1)))) {
                count += 1
            }
        }
        val ratio = count / barCount.toDouble()
        val satisfied = ratio >= minStrenght
        traceIsSatisfied(index, satisfied)
        return satisfied
    }
}
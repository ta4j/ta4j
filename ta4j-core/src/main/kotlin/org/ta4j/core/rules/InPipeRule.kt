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
import org.ta4j.core.indicators.helpers.ConstantIndicator
import org.ta4j.core.num.*

/**
 * Indicator-between-indicators rule.
 *
 * Satisfied when the value of the [indicator] is between the
 * values of the boundary (up/down) indicators.
 */
/**
 * Constructor.
 *
 * @param ref   the reference indicator
 * @param upper the upper indicator
 * @param lower the lower indicator
 */
class InPipeRule
(
    /** The evaluated indicator  */
    private val ref: Indicator<Num>,
    /** The upper indicator  */
    private val upper: Indicator<Num>,
    /** The lower indicator  */
    private val lower: Indicator<Num>
) : AbstractRule() {
    /**
     * Constructor.
     *
     * @param ref   the reference indicator
     * @param upper the upper threshold
     * @param lower the lower threshold
     */
    constructor(ref: Indicator<Num>, upper: Number, lower: Number) : this(ref, ref.numOf(upper), ref.numOf(lower))

    /**
     * Constructor.
     *
     * @param ref   the reference indicator
     * @param upper the upper threshold
     * @param lower the lower threshold
     */
    constructor(ref: Indicator<Num>, upper: Num, lower: Num) : this(
        ref, ConstantIndicator<Num>(ref.barSeries, upper),
        ConstantIndicator<Num>(ref.barSeries, lower)
    )

    /** This rule does not use the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        val satisfied = (ref[index].isLessThanOrEqual(upper[index])
                && ref[index].isGreaterThanOrEqual(lower[index]))
        traceIsSatisfied(index, satisfied)
        return satisfied
    }
}
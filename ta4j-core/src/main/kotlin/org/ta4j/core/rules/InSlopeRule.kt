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
import org.ta4j.core.indicators.helpers.CombineIndicator
import org.ta4j.core.indicators.helpers.PreviousValueIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * Indicator-in-slope rule.
 *
 * Satisfied when the difference of the value of the [indicator][Indicator]
 * and the previous (n-th) value of the [indicator][Indicator] is between
 * the values of maxSlope or/and minSlope. It can test both, positive and
 * negative slope.
 */
/**
 * Constructor.
 *
 * @param ref         the reference indicator
 * @param nthPrevious defines the previous n-th indicator
 * @param minSlope    minumum slope between value of reference and previous
 * indicator
 * @param maxSlope    maximum slope between value of reference and previous
 * indicator
 */

class InSlopeRule(
    /** The actual indicator  */
    private val ref: Indicator<Num>, nthPrevious: Int, private val minSlope: Num, private val maxSlope: Num
) : AbstractRule() {
    /** The previous n-th value of ref  */
    private val  prev = PreviousValueIndicator(ref, nthPrevious)



    /**
     * Constructor.
     *
     * @param ref      the reference indicator
     * @param minSlope minumum slope between reference and previous indicator
     */
    constructor(ref: Indicator<Num>, minSlope: Num) : this(ref, 1, minSlope, NaN.Companion.NaN) {}

    /**
     * Constructor.
     *
     * @param ref      the reference indicator
     * @param minSlope minumum slope between value of reference and previous
     * indicator
     * @param maxSlope maximum slope between value of reference and previous
     * indicator
     */
    constructor(ref: Indicator<Num>, minSlope: Num, maxSlope: Num) : this(ref, 1, minSlope, maxSlope)

    /**
     * Constructor.
     *
     * @param ref         the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param maxSlope    maximum slope between value of reference and previous
     * indicator
     */
    constructor(ref: Indicator<Num>, nthPrevious: Int, maxSlope: Num) : this(
        ref,
        nthPrevious,
        NaN.NaN,
        maxSlope
    )

    init {
    }

    /** This rule does not use the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        val diff: CombineIndicator = CombineIndicator.minus(ref, prev)
        val v = diff[index]
        val minSlopeSatisfied = minSlope.isNaN || v.isGreaterThanOrEqual(minSlope)
        val maxSlopeSatisfied = maxSlope.isNaN || v.isLessThanOrEqual(maxSlope)
        val isNaN = minSlope.isNaN && maxSlope.isNaN
        val satisfied = minSlopeSatisfied && maxSlopeSatisfied && !isNaN
        traceIsSatisfied(index, satisfied)
        return satisfied
    }
}
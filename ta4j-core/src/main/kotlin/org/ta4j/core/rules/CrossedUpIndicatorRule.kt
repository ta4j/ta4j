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
import org.ta4j.core.indicators.helpers.CrossIndicator
import org.ta4j.core.num.*

/**
 * Crossed-up indicator rule.
 *
 * Satisfied when the value of the first [indicator][Indicator] crosses-up
 * the value of the second one.
 */
class CrossedUpIndicatorRule(first: Indicator<Num>, second: Indicator<Num>) : AbstractRule() {
    /** The cross indicator  */
    private val cross: CrossIndicator

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    constructor(indicator: Indicator<Num>, threshold: Number) : this(indicator, indicator.numOf(threshold)) {}

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    constructor(indicator: Indicator<Num>, threshold: Num) : this(
        indicator,
        ConstantIndicator<Num>(indicator.barSeries, threshold)
    ) {
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    init {
        cross = CrossIndicator(second, first)
    }

    /** This rule does not use the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        val satisfied = cross[index]
        traceIsSatisfied(index, satisfied)
        return satisfied
    }

    /**
     * @return the initial lower indicator
     */
    val low: Indicator<Num>
        get() = cross.low

    /**
     * @return the initial upper indicator
     */
    val up: Indicator<Num>
        get() = cross.up
}
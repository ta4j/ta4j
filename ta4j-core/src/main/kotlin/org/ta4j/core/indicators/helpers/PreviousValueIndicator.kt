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
import org.ta4j.core.num.*

/**
 * Returns the previous (n-th) value of an indicator
 */
class PreviousValueIndicator @JvmOverloads constructor(indicator: Indicator<Num>, n: Int = 1) :
    CachedIndicator<Num>(indicator) {
    private val n: Int
    private val indicator: Indicator<Num>
    /**
     * Constructor.
     *
     * @param indicator the indicator of which the previous value should be
     * calculated
     * @param n         parameter defines the previous n-th value
     */
    /**
     * Constructor.
     *
     * @param indicator the indicator of which the previous value should be
     * calculated
     */
    init {
        require(n >= 1) { "n must be positive number, but was: $n" }
        this.n = n
        this.indicator = indicator
    }

    override fun calculate(index: Int): Num {
        val previousValue = Math.max(0, index - n)
        return indicator.getValue(previousValue)
    }

    override fun toString(): String {
        val nInfo = if (n == 1) "" else "($n)"
        return javaClass.simpleName + nInfo + "[" + indicator + "]"
    }
}
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
package org.ta4j.core.indicators.statistics

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.num.*

/**
 * Sigma-Indicator (also called, "z-score" or "standard score").
 *
 * see http://www.statisticshowto.com/probability-and-statistics/z-score/
 */
/**
 * Constructor.
 *
 * @param ref      the indicator
 * @param barCount the time frame
 */

class SigmaIndicator(private val ref: Indicator<Num>, private val barCount: Int) : CachedIndicator<Num>(
    ref
) {
    private val mean: SMAIndicator = SMAIndicator(ref, barCount)
    private val sd: StandardDeviationIndicator = StandardDeviationIndicator(ref, barCount)

    override fun calculate(index: Int): Num {
        // z-score = (ref - mean) / sd
        return (ref[index] - mean[index]) / sd[index]
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
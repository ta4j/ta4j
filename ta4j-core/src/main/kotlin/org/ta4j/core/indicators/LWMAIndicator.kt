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
 * Linearly Weighted Moving Average (LWMA).
 *
 * @see [
 * https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp](https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp)
 */
class LWMAIndicator(private val indicator: Indicator<Num>, private val barCount: Int) : CachedIndicator<Num>(
    indicator
) {
    private val zero = numOf(0)
    override fun calculate(index: Int): Num {
        var sum = zero
        var denominator = zero
        var count = 0
        if (index + 1 < barCount) {
            return zero
        }
        val startIndex = index - barCount + 1
        for (i in startIndex..index) {
            count++
            denominator += numOf(count)
            sum += indicator[i] * numOf(count)
        }
        return sum / denominator
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
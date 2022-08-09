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
 * Zero-lag exponential moving average indicator.
 *
 * @see [
 * http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm](http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm)
 */
class ZLEMAIndicator(private val indicator: Indicator<Num>, private val barCount: Int) : RecursiveCachedIndicator<Num>(
    indicator
) {
    private val k: Num = numOf(2) / numOf(barCount + 1)
    private val lag: Int = (barCount - 1) / 2

    override fun calculate(index: Int): Num {
        if (index + 1 < barCount) {
            // Starting point of the ZLEMA
            return SMAIndicator(indicator, barCount)[index]
        }
        if (index == 0) {
            // If the barCount is bigger than the indicator's value count
            return indicator[0]
        }
        val zlemaPrev = getValue(index - 1)
        return (k * ((numOf(2) * indicator[index]) - indicator[index - lag])) + (numOf(1) - k) * zlemaPrev
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}
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
package org.ta4j.core.indicators.aroon

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.numeric.NumericIndicator

/**
 * A facade to create the two Aroon indicators. The Aroon Oscillator can also be
 * created on demand.
 *
 *
 *
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects.
 */
class AroonFacade(bs: BarSeries?, n: Int) {
    private val up: NumericIndicator
    private val down: NumericIndicator

    /**
     * Create the Aroon facade.
     *
     * @param bs a bar series
     * @param n  the number of periods (barCount) used for the indicators
     */
    init {
        up = NumericIndicator.of(AroonUpIndicator(bs, n))
        down = NumericIndicator.of(AroonDownIndicator(bs, n))
    }

    /**
     * A fluent AroonUp indicator.
     *
     * @return a NumericIndicator wrapped around a cached AroonUpIndicator
     */
    fun up(): NumericIndicator {
        return up
    }

    /**
     * A fluent AroonDown indicator.
     *
     * @return a NumericIndicator wrapped around a cached AroonDownIndicator
     */
    fun down(): NumericIndicator {
        return down
    }

    /**
     * A lightweight fluent AroonOscillator.
     *
     * @return an uncached object that calculates the difference between AoonUp and
     * AroonDown
     */
    fun oscillator(): NumericIndicator? {
        return up.minus(down)
    }
}
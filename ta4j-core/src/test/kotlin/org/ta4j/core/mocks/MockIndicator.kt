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
package org.ta4j.core.mocks

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.num.Num

class MockIndicator
/**
 * Constructor.
 *
 * @param series BarSeries of the Indicator
 * @param values Indicator values
 */(
    /**
     * Gets the Indicator TimeSeries.
     *
     * @return TimeSeries of the Indicator
     */
    override val barSeries: BarSeries?, private val values: List<Num>
) : Indicator<Num> {
    /**
     * Gets a value from the Indicator
     *
     * @param index Indicator value to get
     * @return Num Indicator value at index
     */
    override fun getValue(index: Int): Num {
        return values[index]
    }

    override fun numOf(number: Number): Num {
        return barSeries!!.numOf(number)
    }
}
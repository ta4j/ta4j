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

import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.ExternalIndicatorTest
import org.ta4j.core.IndicatorFactory
import org.ta4j.core.TestUtils
import org.ta4j.core.num.Num
import java.util.function.Function

/**
 * Testing the RWIHighIndicator
 */
class RWIHighIndicatorTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(
    IndicatorFactory { data: BarSeries?, params: Array<out Any?> -> RWIHighIndicator(data, params[0] as Int) }, numFunction
) {
    /**
     * TODO: Just graphically Excel-Sheet validation with hard coded results. Excel
     * formula needed
     */
    private val xls: ExternalIndicatorTest

    init {
        xls = XLSIndicatorTest(this.javaClass, "RWIHL.xls", 8, numFunction)
    }

    @Test
    @Throws(Exception::class)
    fun randomWalkIndexHigh() {
        val series = xls.getSeries()
        val rwih = getIndicator(series, 20) as RWIHighIndicator
        TestUtils.assertIndicatorEquals(getIndicator(series, 20), rwih)
    }
}
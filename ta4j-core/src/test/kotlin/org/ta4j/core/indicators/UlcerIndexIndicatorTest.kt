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

import org.junit.Before
import org.junit.Test
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class UlcerIndexIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    private var ibmData: BarSeries? = null
    @Before
    fun setUp() {
        ibmData = MockBarSeries(
            numFunction, 194.75, 195.00, 195.10, 194.46, 190.60, 188.86, 185.47, 184.46, 182.31,
            185.22, 184.00, 182.87, 187.45, 194.51, 191.63, 190.02, 189.53, 190.27, 193.13, 195.55, 195.84, 195.15,
            194.35, 193.62, 197.68, 197.91, 199.08, 199.03, 198.42, 199.29, 199.01, 198.29, 198.40, 200.84, 201.22,
            200.50, 198.65, 197.25, 195.70, 197.77, 195.69, 194.87, 195.08
        )
    }

    @Test
    fun ulcerIndexUsingBarCount14UsingIBMData() {
        val ulcer = UlcerIndexIndicator(ClosePriceIndicator(ibmData), 14)
        TestUtils.assertNumEquals(0, ulcer[0])
        TestUtils.assertNumEquals(0, ulcer[0])
        TestUtils.assertNumEquals(1.2340096463740846, ulcer.getValue(26))
        TestUtils.assertNumEquals(0.560553282860879, ulcer.getValue(27))
        TestUtils.assertNumEquals(0.39324888828140886, ulcer.getValue(28))
        TestUtils.assertNumEquals(0.38716275079310825, ulcer.getValue(29))
        TestUtils.assertNumEquals(0.3889794194862251, ulcer.getValue(30))
        TestUtils.assertNumEquals(0.4114481689096125, ulcer.getValue(31))
        TestUtils.assertNumEquals(0.42841008722557894, ulcer.getValue(32))
        TestUtils.assertNumEquals(0.42841008722557894, ulcer.getValue(33))
        TestUtils.assertNumEquals(0.3121617589229034, ulcer.getValue(34))
        TestUtils.assertNumEquals(0.2464924497436544, ulcer.getValue(35))
        TestUtils.assertNumEquals(0.4089008481549337, ulcer.getValue(36))
        TestUtils.assertNumEquals(0.667264629592715, ulcer.getValue(37))
        TestUtils.assertNumEquals(0.9913518177402276, ulcer.getValue(38))
        TestUtils.assertNumEquals(1.0921325741850083, ulcer.getValue(39))
        TestUtils.assertNumEquals(1.3156949266800984, ulcer.getValue(40))
        TestUtils.assertNumEquals(1.5606676136361992, ulcer.getValue(41))
    }
}
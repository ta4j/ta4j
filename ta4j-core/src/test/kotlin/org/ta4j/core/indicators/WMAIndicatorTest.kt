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
import org.ta4j.core.Indicator
import org.ta4j.core.TestUtils
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class WMAIndicatorTest(numFunction: Function<Number?, Num>) :
    AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun calculate() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val close: Indicator<Num> = ClosePriceIndicator(series)
        val wmaIndicator: Indicator<Num> = WMAIndicator(close, 3)
        TestUtils.assertNumEquals(1, wmaIndicator[0])
        TestUtils.assertNumEquals(1.6667, wmaIndicator.getValue(1))
        TestUtils.assertNumEquals(2.3333, wmaIndicator.getValue(2))
        TestUtils.assertNumEquals(3.3333, wmaIndicator.getValue(3))
        TestUtils.assertNumEquals(4.3333, wmaIndicator.getValue(4))
        TestUtils.assertNumEquals(5.3333, wmaIndicator.getValue(5))
    }

    @Test
    fun wmaWithBarCountGreaterThanSeriesSize() {
        val series = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val close: Indicator<Num> = ClosePriceIndicator(series)
        val wmaIndicator: Indicator<Num> = WMAIndicator(close, 55)
        TestUtils.assertNumEquals(1, wmaIndicator[0])
        TestUtils.assertNumEquals(1.6667, wmaIndicator.getValue(1))
        TestUtils.assertNumEquals(2.3333, wmaIndicator.getValue(2))
        TestUtils.assertNumEquals(3, wmaIndicator.getValue(3))
        TestUtils.assertNumEquals(3.6666, wmaIndicator.getValue(4))
        TestUtils.assertNumEquals(4.3333, wmaIndicator.getValue(5))
    }

    @Test
    fun wmaUsingBarCount9UsingClosePrice() {
        // Example from
        // http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
        val data: BarSeries = MockBarSeries(
            numFunction, 84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98,
            87.10, 83.11, 83.60, 83.66, 82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87
        )
        val wma = WMAIndicator(ClosePriceIndicator(data), 9)
        TestUtils.assertNumEquals(84.4958, wma.getValue(8))
        TestUtils.assertNumEquals(85.0158, wma.getValue(9))
        TestUtils.assertNumEquals(84.6807, wma.getValue(10))
        TestUtils.assertNumEquals(84.5387, wma.getValue(11))
        TestUtils.assertNumEquals(84.4298, wma.getValue(12))
        TestUtils.assertNumEquals(84.1224, wma.getValue(13))
        TestUtils.assertNumEquals(83.1031, wma.getValue(14))
        TestUtils.assertNumEquals(82.1462, wma.getValue(15))
        TestUtils.assertNumEquals(81.1149, wma.getValue(16))
        TestUtils.assertNumEquals(80.0736, wma.getValue(17))
        TestUtils.assertNumEquals(78.6907, wma.getValue(18))
        TestUtils.assertNumEquals(78.1504, wma.getValue(19))
        TestUtils.assertNumEquals(77.6133, wma.getValue(20))
    }
}
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
package org.ta4j.core.criteria

import org.ta4j.core.AnalysisCriterion
import org.ta4j.core.Position
import org.ta4j.core.TestUtils
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class OpenedPositionUtils {
    fun testCalculateOneOpenPositionShouldReturnExpectedValue(
        numFunction: Function<Number?, Num>,
        criterion: AnalysisCriterion?, expectedValue: Num?
    ) {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 110.0, 100.0, 95.0, 105.0)
        val trade = Position(TradeType.BUY)
        trade.operate(0, series.numOf(2.5), series.numOf(1))
        val value = criterion!!.calculate(series, trade)
        TestUtils.assertNumEquals(expectedValue, value)
    }

    fun testCalculateOneOpenPositionShouldReturnExpectedValue(
        numFunction: Function<Number?, Num>,
        criterion: AnalysisCriterion?, expectedValue: Int
    ) {
        this.testCalculateOneOpenPositionShouldReturnExpectedValue(
            numFunction, criterion,
            numFunction.apply(expectedValue)
        )
    }
}
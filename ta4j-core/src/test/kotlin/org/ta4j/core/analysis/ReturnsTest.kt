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
package org.ta4j.core.analysis

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.DecimalNum.Companion.valueOf
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.util.function.Function

class ReturnsTest(numFunction: Function<Number?, Num>?) : AbstractIndicatorTest<Indicator<Num>?, Num>(
    Function { obj: Number? -> DoubleNum.Companion.valueOf(obj) }) {
    @Test
    fun returnSize() {
        for (type in Returns.ReturnType.values()) {
            // No return at index 0
            val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 5.0)
            val returns = Returns(sampleBarSeries, BaseTradingRecord(), type)
            Assert.assertEquals(4, returns.size.toLong())
        }
    }

    @Test
    fun singleReturnPositionArith() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(1, sampleBarSeries)
        )
        val return1 = Returns(sampleBarSeries, tradingRecord, Returns.ReturnType.ARITHMETIC)
        TestUtils.assertNumEquals(NaN.NaN, return1[0])
        TestUtils.assertNumEquals(1.0, return1.getValue(1))
    }

    @Test
    fun returnsWithSellAndBuyTrades() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 2.0, 1.0, 3.0, 5.0, 6.0, 3.0, 20.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(1, sampleBarSeries), buyAt(3, sampleBarSeries), sellAt(4, sampleBarSeries),
            sellAt(5, sampleBarSeries), buyAt(6, sampleBarSeries)
        )
        val strategyReturns = Returns(sampleBarSeries, tradingRecord, Returns.ReturnType.ARITHMETIC)
        TestUtils.assertNumEquals(NaN.NaN, strategyReturns[0])
        TestUtils.assertNumEquals(-0.5, strategyReturns.getValue(1))
        TestUtils.assertNumEquals(0, strategyReturns.getValue(2))
        TestUtils.assertNumEquals(0, strategyReturns.getValue(3))
        TestUtils.assertNumEquals(1.0 / 5, strategyReturns.getValue(4))
        TestUtils.assertNumEquals(0, strategyReturns.getValue(5))
        TestUtils.assertNumEquals(1 - 20.0 / 3, strategyReturns.getValue(6))
    }

    @Test
    fun returnsWithGaps() {
        val sampleBarSeries: BarSeries =
            MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(2, sampleBarSeries),
            buyAt(5, sampleBarSeries), buyAt(8, sampleBarSeries), sellAt(10, sampleBarSeries)
        )
        val returns = Returns(sampleBarSeries, tradingRecord, Returns.ReturnType.LOG)
        TestUtils.assertNumEquals(NaN.NaN, returns[0])
        TestUtils.assertNumEquals(0, returns.getValue(1))
        TestUtils.assertNumEquals(0, returns.getValue(2))
        TestUtils.assertNumEquals(-0.28768207245178085, returns.getValue(3))
        TestUtils.assertNumEquals(-0.22314355131420976, returns.getValue(4))
        TestUtils.assertNumEquals(-0.1823215567939546, returns.getValue(5))
        TestUtils.assertNumEquals(0, returns.getValue(6))
        TestUtils.assertNumEquals(0, returns.getValue(7))
        TestUtils.assertNumEquals(0, returns.getValue(8))
        TestUtils.assertNumEquals(0.10536051565782635, returns.getValue(9))
        TestUtils.assertNumEquals(0.09531017980432493, returns.getValue(10))
        TestUtils.assertNumEquals(0, returns.getValue(11))
    }

    @Test
    fun returnsWithNoPositions() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 3.0, 2.0, 5.0, 4.0, 7.0, 6.0, 7.0, 8.0, 5.0, 6.0)
        val returns = Returns(sampleBarSeries, BaseTradingRecord(), Returns.ReturnType.LOG)
        TestUtils.assertNumEquals(NaN.NaN, returns[0])
        TestUtils.assertNumEquals(0, returns.getValue(4))
        TestUtils.assertNumEquals(0, returns.getValue(7))
        TestUtils.assertNumEquals(0, returns.getValue(9))
    }

    @Test
    fun returnsPrecision() {
        val doubleSeries: BarSeries = MockBarSeries(numFunction, 1.2, 1.1)
        val precisionSeries: BarSeries =
            MockBarSeries({ obj: Number? -> DecimalNum.Companion.valueOf(obj) }, 1.2, 1.1)
        val fullRecordDouble: TradingRecord = BaseTradingRecord()
        fullRecordDouble.enter(
            doubleSeries.beginIndex, doubleSeries.getBar(0).closePrice,
            doubleSeries.numOf(1)
        )
        fullRecordDouble.exit(
            doubleSeries.endIndex, doubleSeries.getBar(1).closePrice,
            doubleSeries.numOf(1)
        )
        val fullRecordPrecision: TradingRecord = BaseTradingRecord()
        fullRecordPrecision.enter(
            precisionSeries.beginIndex, precisionSeries.getBar(0).closePrice,
            precisionSeries.numOf(1)
        )
        fullRecordPrecision.exit(
            precisionSeries.endIndex, precisionSeries.getBar(1).closePrice,
            precisionSeries.numOf(1)
        )

        // Return calculation DoubleNum vs PrecisionNum
        val arithDouble = Returns(doubleSeries, fullRecordDouble, Returns.ReturnType.ARITHMETIC).getValue(1)
        val arithPrecision = Returns(precisionSeries, fullRecordPrecision, Returns.ReturnType.ARITHMETIC)
            .getValue(1)
        val logDouble = Returns(doubleSeries, fullRecordDouble, Returns.ReturnType.LOG).getValue(1)
        val logPrecision = Returns(precisionSeries, fullRecordPrecision, Returns.ReturnType.LOG).getValue(1)
        TestUtils.assertNumEquals(arithDouble, DoubleNum.valueOf(-0.08333333333333326))
        TestUtils.assertNumEquals(
            arithPrecision,
            valueOf(1.1).div(valueOf(1.2)).minus(valueOf(1))
        )
        TestUtils.assertNumEquals(logDouble, DoubleNum.valueOf(-0.08701137698962969))
        TestUtils.assertNumEquals(logPrecision, valueOf("-0.087011376989629766167765901873746"))
    }
}
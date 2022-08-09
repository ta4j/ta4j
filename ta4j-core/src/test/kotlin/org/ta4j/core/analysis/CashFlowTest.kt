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
import org.ta4j.core.mocks.MockBar
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.*
import java.util.function.Function

class CashFlowTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Indicator<Num>?, Num>(numFunction) {
    @Test
    fun cashFlowSize() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0, 3.0, 4.0, 5.0)
        val cashFlow = CashFlow(sampleBarSeries, BaseTradingRecord())
        Assert.assertEquals(5, cashFlow.size.toLong())
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(1, cashFlow.getValue(2))
        TestUtils.assertNumEquals(1, cashFlow.getValue(3))
        TestUtils.assertNumEquals(1, cashFlow.getValue(4))
    }

    @Test
    fun cashFlowBuyWithOnlyOnePosition() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(1, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(2, cashFlow.getValue(1))
    }

    @Test
    fun cashFlowWithSellAndBuyTrades() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 2.0, 1.0, 3.0, 5.0, 6.0, 3.0, 20.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(1, sampleBarSeries), buyAt(3, sampleBarSeries), sellAt(4, sampleBarSeries),
            sellAt(5, sampleBarSeries), buyAt(6, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals("0.5", cashFlow.getValue(1))
        TestUtils.assertNumEquals("0.5", cashFlow.getValue(2))
        TestUtils.assertNumEquals("0.5", cashFlow.getValue(3))
        TestUtils.assertNumEquals("0.6", cashFlow.getValue(4))
        TestUtils.assertNumEquals("0.6", cashFlow.getValue(5))
        TestUtils.assertNumEquals("-2.8", cashFlow.getValue(6))
    }

    @Test
    fun cashFlowSell() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(2, sampleBarSeries),
            buyAt(3, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(1, cashFlow.getValue(2))
        TestUtils.assertNumEquals(0, cashFlow.getValue(3))
        TestUtils.assertNumEquals(0, cashFlow.getValue(4))
        TestUtils.assertNumEquals(0, cashFlow.getValue(5))
    }

    @Test
    fun cashFlowShortSell() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(2, sampleBarSeries), sellAt(2, sampleBarSeries), buyAt(4, sampleBarSeries),
            buyAt(4, sampleBarSeries), sellAt(5, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(2, cashFlow.getValue(1))
        TestUtils.assertNumEquals(4, cashFlow.getValue(2))
        TestUtils.assertNumEquals(0, cashFlow.getValue(3))
        TestUtils.assertNumEquals(-8, cashFlow.getValue(4))
        TestUtils.assertNumEquals(-8, cashFlow.getValue(5))
    }

    @Test
    fun cashFlowShortSellWith20PercentGain() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 110.0, 100.0, 90.0, 80.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(1, sampleBarSeries),
            buyAt(3, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(1.1, cashFlow.getValue(2))
        TestUtils.assertNumEquals(1.2, cashFlow.getValue(3))
    }

    @Test
    fun cashFlowShortSellWith20PercentLoss() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 90.0, 100.0, 110.0, 120.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(1, sampleBarSeries),
            buyAt(3, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(0.9, cashFlow.getValue(2))
        TestUtils.assertNumEquals(0.8, cashFlow.getValue(3))
    }

    @Test
    fun cashFlowShortSellWith100PercentLoss() {
        val sampleBarSeries: BarSeries = MockBarSeries(
            numFunction, 90.0, 100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0,
            200.0
        )
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(1, sampleBarSeries),
            buyAt(11, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(0.9, cashFlow.getValue(2))
        TestUtils.assertNumEquals(0.8, cashFlow.getValue(3))
        TestUtils.assertNumEquals(0.7, cashFlow.getValue(4))
        TestUtils.assertNumEquals(0.6, cashFlow.getValue(5))
        TestUtils.assertNumEquals(0.5, cashFlow.getValue(6))
        TestUtils.assertNumEquals(0.4, cashFlow.getValue(7))
        TestUtils.assertNumEquals(0.3, cashFlow.getValue(8))
        TestUtils.assertNumEquals(0.2, cashFlow.getValue(9))
        TestUtils.assertNumEquals(0.1, cashFlow.getValue(10))
        TestUtils.assertNumEquals(0.0, cashFlow.getValue(11))
    }

    @Test
    fun cashFlowShortSellWithOver100PercentLoss() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 100.0, 150.0, 200.0, 210.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(0, sampleBarSeries),
            buyAt(3, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(0.5, cashFlow.getValue(1))
        TestUtils.assertNumEquals(0.0, cashFlow.getValue(2))
        TestUtils.assertNumEquals(-0.1, cashFlow.getValue(3))
    }

    @Test
    fun cashFlowShortSellBigLossWithNegativeCashFlow() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 3.0, 20.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            sellAt(0, sampleBarSeries),
            buyAt(1, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(-4.6667, cashFlow.getValue(1))
    }

    @Test
    fun cashFlowValueWithOnlyOnePositionAndAGapBefore() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 1.0, 2.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(1, sampleBarSeries),
            sellAt(2, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(2, cashFlow.getValue(2))
    }

    @Test
    fun cashFlowValueWithOnlyOnePositionAndAGapAfter() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0, 2.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(1, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        Assert.assertEquals(3, cashFlow.size.toLong())
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(2, cashFlow.getValue(1))
        TestUtils.assertNumEquals(2, cashFlow.getValue(2))
    }

    @Test
    fun cashFlowValueWithTwoPositionsAndLongTimeWithoutTrades() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(1, sampleBarSeries),
            sellAt(2, sampleBarSeries), buyAt(4, sampleBarSeries), sellAt(5, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(1, cashFlow.getValue(1))
        TestUtils.assertNumEquals(2, cashFlow.getValue(2))
        TestUtils.assertNumEquals(2, cashFlow.getValue(3))
        TestUtils.assertNumEquals(2, cashFlow.getValue(4))
        TestUtils.assertNumEquals(4, cashFlow.getValue(5))
    }

    @Test
    fun cashFlowValue() {
        // First sample series
        var sampleBarSeries: BarSeries = MockBarSeries(
            numFunction, 3.0, 2.0, 5.0, 1000.0, 5000.0, 0.0001, 4.0, 7.0, 6.0, 7.0,
            8.0, 5.0, 6.0
        )
        var tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(2, sampleBarSeries), buyAt(6, sampleBarSeries), sellAt(8, sampleBarSeries),
            buyAt(9, sampleBarSeries), sellAt(11, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow[0])
        TestUtils.assertNumEquals(2.0 / 3, cashFlow.getValue(1))
        TestUtils.assertNumEquals(5.0 / 3, cashFlow.getValue(2))
        TestUtils.assertNumEquals(5.0 / 3, cashFlow.getValue(3))
        TestUtils.assertNumEquals(5.0 / 3, cashFlow.getValue(4))
        TestUtils.assertNumEquals(5.0 / 3, cashFlow.getValue(5))
        TestUtils.assertNumEquals(5.0 / 3, cashFlow.getValue(6))
        TestUtils.assertNumEquals(5.0 / 3 * 7.0 / 4, cashFlow.getValue(7))
        TestUtils.assertNumEquals(5.0 / 3 * 6.0 / 4, cashFlow.getValue(8))
        TestUtils.assertNumEquals(5.0 / 3 * 6.0 / 4, cashFlow.getValue(9))
        TestUtils.assertNumEquals(5.0 / 3 * 6.0 / 4 * 8.0 / 7, cashFlow.getValue(10))
        TestUtils.assertNumEquals(5.0 / 3 * 6.0 / 4 * 5.0 / 7, cashFlow.getValue(11))
        TestUtils.assertNumEquals(5.0 / 3 * 6.0 / 4 * 5.0 / 7, cashFlow.getValue(12))

        // Second sample series
        sampleBarSeries = MockBarSeries(numFunction, 5.0, 6.0, 3.0, 7.0, 8.0, 6.0, 10.0, 15.0, 6.0)
        tradingRecord = BaseTradingRecord(
            buyAt(4, sampleBarSeries), sellAt(5, sampleBarSeries),
            buyAt(6, sampleBarSeries), sellAt(8, sampleBarSeries)
        )
        val flow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, flow[0])
        TestUtils.assertNumEquals(1, flow.getValue(1))
        TestUtils.assertNumEquals(1, flow.getValue(2))
        TestUtils.assertNumEquals(1, flow.getValue(3))
        TestUtils.assertNumEquals(1, flow.getValue(4))
        TestUtils.assertNumEquals("0.75", flow.getValue(5))
        TestUtils.assertNumEquals("0.75", flow.getValue(6))
        TestUtils.assertNumEquals("1.125", flow.getValue(7))
        TestUtils.assertNumEquals("0.45", flow.getValue(8))
    }

    @Test
    fun cashFlowValueWithNoPositions() {
        val sampleBarSeries: BarSeries = MockBarSeries(numFunction, 3.0, 2.0, 5.0, 4.0, 7.0, 6.0, 7.0, 8.0, 5.0, 6.0)
        val cashFlow = CashFlow(sampleBarSeries, BaseTradingRecord())
        TestUtils.assertNumEquals(1, cashFlow.getValue(4))
        TestUtils.assertNumEquals(1, cashFlow.getValue(7))
        TestUtils.assertNumEquals(1, cashFlow.getValue(9))
    }

    @Test
    fun reallyLongCashFlow() {
        val size = 1000000
        val sampleBarSeries: BarSeries = MockBarSeries(Collections.nCopies<Bar>(size, MockBar(10.0, numFunction)))
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, sampleBarSeries),
            sellAt(size - 1, sampleBarSeries)
        )
        val cashFlow = CashFlow(sampleBarSeries, tradingRecord)
        TestUtils.assertNumEquals(1, cashFlow.getValue(size - 1))
    }
}
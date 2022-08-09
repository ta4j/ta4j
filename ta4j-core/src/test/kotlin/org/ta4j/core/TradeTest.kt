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
package org.ta4j.core

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.analysis.cost.CostModel
import org.ta4j.core.analysis.cost.LinearTransactionCostModel
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import java.util.function.Function

class TradeTest {
    var opEquals1: Trade? = null
    var opEquals2: Trade? = null
    var opNotEquals1: Trade? = null
    var opNotEquals2: Trade? = null
    @Before
    fun setUp() {
        opEquals1 = buyAt(1, NaN.NaN, NaN.NaN)
        opEquals2 = buyAt(1, NaN.NaN, NaN.NaN)
        opNotEquals1 = sellAt(1, NaN.NaN, NaN.NaN)
        opNotEquals2 = buyAt(2, NaN.NaN, NaN.NaN)
    }

    @Test
    fun type() {
        Assert.assertEquals(TradeType.SELL, opNotEquals1!!.type)
        Assert.assertFalse(opNotEquals1!!.isBuy)
        Assert.assertTrue(opNotEquals1!!.isSell)
        Assert.assertEquals(TradeType.BUY, opNotEquals2!!.type)
        Assert.assertTrue(opNotEquals2!!.isBuy)
        Assert.assertFalse(opNotEquals2!!.isSell)
    }

    @Test
    fun overrideToString() {
        Assert.assertEquals(opEquals1.toString(), opEquals2.toString())
        Assert.assertNotEquals(opEquals1.toString(), opNotEquals1.toString())
        Assert.assertNotEquals(opEquals1.toString(), opNotEquals2.toString())
    }

    @Test
    fun initializeWithCostsTest() {
        val transactionCostModel: CostModel = LinearTransactionCostModel(0.05)
        val trade = Trade(0, TradeType.BUY, DoubleNum.valueOf(100), DoubleNum.valueOf(20), transactionCostModel)
        val expectedCost: Num = DoubleNum.valueOf(100)
        val expectedValue: Num = DoubleNum.valueOf(2000)
        val expectedRawPrice: Num = DoubleNum.valueOf(100)
        val expectedNetPrice: Num = DoubleNum.valueOf(105)
        TestUtils.assertNumEquals(expectedCost, trade.cost)
        TestUtils.assertNumEquals(expectedValue, trade.value)
        TestUtils.assertNumEquals(expectedRawPrice, trade.pricePerAsset)
        TestUtils.assertNumEquals(expectedNetPrice, trade.netPrice)
        Assert.assertTrue(transactionCostModel.equals(trade.costModel))
    }

    @Test
    fun testReturnBarSeriesCloseOnNaN() {
        val series = MockBarSeries(
            Function<Number?, Num> { obj: Number? -> DoubleNum.Companion.valueOf(obj) },
            100.0,
            95.0,
            100.0,
            80.0,
            85.0,
            130.0
        )
        val trade = Trade(1, TradeType.BUY, NaN.NaN)
        TestUtils.assertNumEquals(DoubleNum.valueOf(95), trade.getPricePerAsset(series))
    }
}
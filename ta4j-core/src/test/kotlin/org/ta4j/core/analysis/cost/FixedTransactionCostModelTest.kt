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
package org.ta4j.core.analysis.cost

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num

import org.junit.Test
import org.ta4j.core.*
import java.util.*

class FixedTransactionCostModelTest {
    @Test
    fun calculatePerPositionWhenPositionIsOpen() {
        val positionTrades = 1.0
        val feePerTrade = RANDOM.nextDouble()
        val model = FixedTransactionCostModel(feePerTrade)
        val position = Position(TradeType.BUY, model, null)
        position.operate(0, PRICE, AMOUNT)
        val cost = model.calculate(position)
        TestUtils.assertNumEquals(cost, DoubleNum.valueOf(feePerTrade * positionTrades))
    }

    @Test
    fun calculatePerPositionWhenPositionIsClosed() {
        val positionTrades = 2.0
        val feePerTrade = RANDOM.nextDouble()
        val model = FixedTransactionCostModel(feePerTrade)
        val holdingPeriod = 2
        val entry = buyAt(0, PRICE, AMOUNT, model)
        val exit = sellAt(holdingPeriod, PRICE, AMOUNT, model)
        val position = Position(entry, exit, model, model)
        val cost = model.calculate(position, RANDOM.nextInt())
        TestUtils.assertNumEquals(cost, DoubleNum.valueOf(feePerTrade * positionTrades))
    }

    @Test
    fun calculatePerPrice() {
        val feePerTrade = RANDOM.nextDouble()
        val model = FixedTransactionCostModel(feePerTrade)
        val cost = model.calculate(PRICE, AMOUNT)
        TestUtils.assertNumEquals(cost, DoubleNum.valueOf(feePerTrade))
    }

    @Test
    fun testEquality() {
        val randomFee = RANDOM.nextDouble()
        val model = FixedTransactionCostModel(randomFee)
        val modelSame: CostModel = FixedTransactionCostModel(randomFee)
        val modelOther: CostModel = LinearTransactionCostModel(randomFee)
        val equality = model.equals(modelSame)
        val inequality = model.equals(modelOther)
        assertTrue(equality)
        assertFalse(inequality)
    }

    companion object {
        private val RANDOM = Random()
        private val PRICE: Num = DoubleNum.valueOf(100)
        private val AMOUNT: Num = DoubleNum.valueOf(5)
    }
}
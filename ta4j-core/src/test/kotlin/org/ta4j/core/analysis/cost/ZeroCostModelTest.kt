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

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.Position
import org.ta4j.core.TestUtils
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.num.DoubleNum

class ZeroCostModelTest {
    @Test
    fun calculatePerPosition() {
        // calculate costs per position
        val model = ZeroCostModel()
        val holdingPeriod = 2
        val entry = buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), model)
        val exit = sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), model)
        val position = Position(entry, exit, model, model)
        val cost = model.calculate(position, holdingPeriod)
        TestUtils.assertNumEquals(cost, DoubleNum.valueOf(0))
    }

    @Test
    fun calculatePerPrice() {
        // calculate costs per position
        val model = ZeroCostModel()
        val cost = model.calculate(DoubleNum.valueOf(100), DoubleNum.valueOf(1))
        TestUtils.assertNumEquals(cost, DoubleNum.valueOf(0))
    }

    @Test
    fun testEquality() {
        val model = ZeroCostModel()
        val modelSame: CostModel = ZeroCostModel()
        val modelOther: CostModel = LinearTransactionCostModel(0.1)
        val equality = model.equals(modelSame)
        val inequality = model.equals(modelOther)
        Assert.assertTrue(equality)
        Assert.assertFalse(inequality)
    }
}
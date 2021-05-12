/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class TradeTest {

    Trade opEquals1, opEquals2, opNotEquals1, opNotEquals2;

    @Before
    public void setUp() {
        opEquals1 = Trade.buyAt(1, NaN, NaN);
        opEquals2 = Trade.buyAt(1, NaN, NaN);

        opNotEquals1 = Trade.sellAt(1, NaN, NaN);
        opNotEquals2 = Trade.buyAt(2, NaN, NaN);
    }

    @Test
    public void type() {
        assertEquals(TradeType.SELL, opNotEquals1.getType());
        assertFalse(opNotEquals1.isBuy());
        assertTrue(opNotEquals1.isSell());
        assertEquals(TradeType.BUY, opNotEquals2.getType());
        assertTrue(opNotEquals2.isBuy());
        assertFalse(opNotEquals2.isSell());
    }

    @Test
    public void overrideToString() {
        assertEquals(opEquals1.toString(), opEquals2.toString());

        assertNotEquals(opEquals1.toString(), opNotEquals1.toString());
        assertNotEquals(opEquals1.toString(), opNotEquals2.toString());
    }

    @Test
    public void initializeWithCostsTest() {
        CostModel transactionCostModel = new LinearTransactionCostModel(0.05);
        Trade trade = new Trade(0, TradeType.BUY, DoubleNum.valueOf(100), DoubleNum.valueOf(20), transactionCostModel);
        Num expectedCost = DoubleNum.valueOf(100);
        Num expectedValue = DoubleNum.valueOf(2000);
        Num expectedRawPrice = DoubleNum.valueOf(100);
        Num expectedNetPrice = DoubleNum.valueOf(105);

        assertNumEquals(expectedCost, trade.getCost());
        assertNumEquals(expectedValue, trade.getValue());
        assertNumEquals(expectedRawPrice, trade.getPricePerAsset());
        assertNumEquals(expectedNetPrice, trade.getNetPrice());
        assertTrue(transactionCostModel.equals(trade.getCostModel()));
    }

    @Test
    public void testReturnBarSeriesCloseOnNaN() {
        MockBarSeries series = new MockBarSeries(DoubleNum::valueOf, 100, 95, 100, 80, 85, 130);
        Trade trade = new Trade(1, TradeType.BUY, NaN);
        assertNumEquals(DoubleNum.valueOf(95), trade.getPricePerAsset(series));
    }
}

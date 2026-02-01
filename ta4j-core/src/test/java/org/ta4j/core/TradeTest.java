/*
 * SPDX-License-Identifier: MIT
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
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

public class TradeTest {

    TradeView opEquals1, opEquals2, opNotEquals1, opNotEquals2;

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
        var transactionCostModel = new LinearTransactionCostModel(0.05);
        var trade = new ModeledTrade(0, TradeType.BUY, DoubleNum.valueOf(100), DoubleNum.valueOf(20),
                transactionCostModel);
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
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95, 100, 80, 85, 130)
                .build();
        TradeView trade = new ModeledTrade(1, TradeType.BUY, NaN);
        assertNumEquals(DoubleNum.valueOf(95), trade.getPricePerAsset(series));
    }
}

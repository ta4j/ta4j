/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
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
        Trade trade = new ModeledTrade(1, TradeType.BUY, NaN);
        assertNumEquals(DoubleNum.valueOf(95), trade.getPricePerAsset(series));
    }

    @Test
    public void factoryBuyAtSeriesOverloads() {
        var numFactory = DoubleNumFactory.getInstance();
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Trade trade = Trade.buyAt(1, series);
        assertEquals(TradeType.BUY, trade.getType());
        assertNumEquals(numFactory.numOf(110), trade.getPricePerAsset());
        assertNumEquals(numFactory.one(), trade.getAmount());
        assertNumEquals(numFactory.numOf(110), trade.getNetPrice());

        Trade tradeWithAmount = Trade.buyAt(1, series, numFactory.two());
        assertEquals(TradeType.BUY, tradeWithAmount.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithAmount.getAmount());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getNetPrice());

        var costModel = new FixedTransactionCostModel(1.0);
        Trade tradeWithCost = Trade.buyAt(1, series, numFactory.two(), costModel);
        assertEquals(TradeType.BUY, tradeWithCost.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithCost.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithCost.getAmount());
        assertNumEquals(numFactory.one(), tradeWithCost.getCost());
        assertNumEquals(numFactory.numOf(110.5), tradeWithCost.getNetPrice());
        assertTrue(costModel.equals(tradeWithCost.getCostModel()));
    }

    @Test
    public void factorySellAtSeriesOverloads() {
        var numFactory = DoubleNumFactory.getInstance();
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Trade trade = Trade.sellAt(1, series);
        assertEquals(TradeType.SELL, trade.getType());
        assertNumEquals(numFactory.numOf(110), trade.getPricePerAsset());
        assertNumEquals(numFactory.one(), trade.getAmount());
        assertNumEquals(numFactory.numOf(110), trade.getNetPrice());

        Trade tradeWithAmount = Trade.sellAt(1, series, numFactory.two());
        assertEquals(TradeType.SELL, tradeWithAmount.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithAmount.getAmount());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getNetPrice());

        var costModel = new FixedTransactionCostModel(1.0);
        Trade tradeWithCost = Trade.sellAt(1, series, numFactory.two(), costModel);
        assertEquals(TradeType.SELL, tradeWithCost.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithCost.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithCost.getAmount());
        assertNumEquals(numFactory.one(), tradeWithCost.getCost());
        assertNumEquals(numFactory.numOf(109.5), tradeWithCost.getNetPrice());
        assertTrue(costModel.equals(tradeWithCost.getCostModel()));
    }

    @Test
    public void factoryBuyAtPriceAmountWithoutCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.buyAt(0, numFactory.numOf(200), numFactory.numOf(4));

        assertEquals(TradeType.BUY, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.zero(), trade.getCost());
        assertNumEquals(numFactory.numOf(200), trade.getNetPrice());
    }

    @Test
    public void factorySellAtPriceAmountWithoutCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.sellAt(0, numFactory.numOf(200), numFactory.numOf(4));

        assertEquals(TradeType.SELL, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.zero(), trade.getCost());
        assertNumEquals(numFactory.numOf(200), trade.getNetPrice());
    }

    @Test
    public void factoryBuyAtPriceAmountWithCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        var costModel = new FixedTransactionCostModel(1.0);
        Trade trade = Trade.buyAt(0, numFactory.numOf(200), numFactory.numOf(4), costModel);

        assertEquals(TradeType.BUY, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.one(), trade.getCost());
        assertNumEquals(numFactory.numOf(200.25), trade.getNetPrice());
        assertTrue(costModel.equals(trade.getCostModel()));
    }

    @Test
    public void factorySellAtPriceAmountWithCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        var costModel = new FixedTransactionCostModel(1.0);
        Trade trade = Trade.sellAt(0, numFactory.numOf(200), numFactory.numOf(4), costModel);

        assertEquals(TradeType.SELL, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.one(), trade.getCost());
        assertNumEquals(numFactory.numOf(199.75), trade.getNetPrice());
        assertTrue(costModel.equals(trade.getCostModel()));
    }

    @Test
    public void defaultAccessorsReturnNull() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.buyAt(0, numFactory.hundred(), numFactory.one());

        assertNull(trade.getTime());
        assertNull(trade.getId());
        assertNull(trade.getInstrument());
        assertNull(trade.getOrderId());
        assertNull(trade.getCorrelationId());
    }

    @Test
    public void zeroAmountDoesNotAdjustNetPrice() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade buyTrade = Trade.buyAt(0, numFactory.hundred(), numFactory.zero());
        Trade sellTrade = Trade.sellAt(0, numFactory.hundred(), numFactory.zero());

        assertNumEquals(numFactory.hundred(), buyTrade.getNetPrice());
        assertNumEquals(numFactory.hundred(), sellTrade.getNetPrice());
    }
}

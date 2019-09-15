/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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
package org.ta4j.core.cost;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.ta4j.core.Order;
import org.ta4j.core.Trade;

public class LinearTransactionCostModelTest {

    private CostModel transactionModel;

    @Before
    public void setUp() throws Exception {
        transactionModel = new LinearTransactionCostModel(0.01);
    }

    @Test
    public void calculateSingleOrderCost() {
        // Price - Amount calculation Test
        Num price = DoubleNum.valueOf(100);
        Num amount = DoubleNum.valueOf(2);
        Num cost = transactionModel.calculate(price, amount);

        assertNumEquals(DoubleNum.valueOf(2), cost);
    }

    @Test
    public void calculateBuyTrade() {
        // Calculate the transaction costs of a closed long trade
        int holdingPeriod = 2;
        Order entry = Order.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        Order exit = Order.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        Trade trade = new Trade(entry, exit, transactionModel, new ZeroCostModel());

        Num costFromBuy = entry.getCost();
        Num costFromSell = exit.getCost();
        Num costsFromModel = transactionModel.calculate(trade, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateSellTrade() {
        // Calculate the transaction costs of a closed short trade
        int holdingPeriod = 2;
        Order entry = Order.sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        Order exit = Order.buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        Trade trade = new Trade(entry, exit, transactionModel, new ZeroCostModel());

        Num costFromBuy = entry.getCost();
        Num costFromSell = exit.getCost();
        Num costsFromModel = transactionModel.calculate(trade, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateOpenSellTrade() {
        // Calculate the transaction costs of an open trade
        int currentIndex = 4;
        Trade trade = new Trade(Order.OrderType.BUY, transactionModel, new ZeroCostModel());
        trade.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num costsFromModel = transactionModel.calculate(trade, currentIndex);

        assertNumEquals(costsFromModel, DoubleNum.valueOf(1));
    }

    @Test
    public void testEquality() {
        LinearTransactionCostModel model = new LinearTransactionCostModel(0.1);
        CostModel modelSameClass = new LinearTransactionCostModel(0.2);
        CostModel modelSameFee = new LinearTransactionCostModel(0.1);
        CostModel modelOther = new ZeroCostModel();

        boolean equality = model.equals(modelSameFee);
        boolean inequality1 = model.equals(modelSameClass);
        boolean inequality2 = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality1);
        assertFalse(inequality2);
    }
}
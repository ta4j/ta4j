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
package org.ta4j.core.cost;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Pos;
import org.ta4j.core.PosPair;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

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
    public void calculateBuyPosition() {
        // Calculate the transaction costs of a closed long position
        int holdingPeriod = 2;
        Pos entry = Pos.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        Pos exit = Pos.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        PosPair posPair = new PosPair(entry, exit, transactionModel, new ZeroCostModel());

        Num costFromBuy = entry.getCost();
        Num costFromSell = exit.getCost();
        Num costsFromModel = transactionModel.calculate(posPair, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateSellPosition() {
        // Calculate the transaction costs of a closed short position
        int holdingPeriod = 2;
        Pos entry = Pos.sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        Pos exit = Pos.buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        PosPair posPair = new PosPair(entry, exit, transactionModel, new ZeroCostModel());

        Num costFromBuy = entry.getCost();
        Num costFromSell = exit.getCost();
        Num costsFromModel = transactionModel.calculate(posPair, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateOpenSellPosition() {
        // Calculate the transaction costs of an open position
        int currentIndex = 4;
        PosPair posPair = new PosPair(Pos.PosType.BUY, transactionModel, new ZeroCostModel());
        posPair.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num costsFromModel = transactionModel.calculate(posPair, currentIndex);

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
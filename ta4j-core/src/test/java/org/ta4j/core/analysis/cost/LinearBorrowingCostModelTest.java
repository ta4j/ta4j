/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class LinearBorrowingCostModelTest {

    private CostModel borrowingModel;

    @Before
    public void setUp() throws Exception {
        borrowingModel = new LinearBorrowingCostModel(0.01);
    }

    @Test
    public void calculateZeroTest() {
        // Price - Amount calculation Test
        Num price = DoubleNum.valueOf(100);
        Num amount = DoubleNum.valueOf(2);
        Num cost = borrowingModel.calculate(price, amount);

        assertNumEquals(DoubleNum.valueOf(0), cost);
    }

    @Test
    public void calculateBuyPosition() {
        // Holding a bought asset should not incur borrowing costs
        int holdingPeriod = 2;
        Trade entry = Trade.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));
        Trade exit = Trade.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1));

        Position position = new Position(entry, exit, new ZeroCostModel(), borrowingModel);

        Num costsFromPosition = position.getHoldingCost();
        Num costsFromModel = borrowingModel.calculate(position, holdingPeriod);

        assertNumEquals(costsFromModel, costsFromPosition);
        assertNumEquals(costsFromModel, DoubleNum.valueOf(0));
    }

    @Test
    public void calculateSellPosition() {
        // Short selling incurs borrowing costs
        int holdingPeriod = 2;
        Trade entry = Trade.sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));
        Trade exit = Trade.buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1));

        Position position = new Position(entry, exit, new ZeroCostModel(), borrowingModel);

        Num costsFromPosition = position.getHoldingCost();
        Num costsFromModel = borrowingModel.calculate(position, holdingPeriod);

        assertNumEquals(costsFromModel, costsFromPosition);
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2));
    }

    @Test
    public void calculateOpenSellPosition() {
        // Short selling incurs borrowing costs. Since position is still open, accounted
        // for until current index
        int currentIndex = 4;
        Position position = new Position(Trade.TradeType.SELL, new ZeroCostModel(), borrowingModel);
        position.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num costsFromPosition = position.getHoldingCost(currentIndex);
        Num costsFromModel = borrowingModel.calculate(position, currentIndex);

        assertNumEquals(costsFromModel, costsFromPosition);
        assertNumEquals(costsFromModel, DoubleNum.valueOf(4));
    }

    @Test
    public void testEquality() {
        LinearBorrowingCostModel model = new LinearBorrowingCostModel(0.1);
        CostModel modelSameClass = new LinearBorrowingCostModel(0.2);
        CostModel modelSameFee = new LinearBorrowingCostModel(0.1);
        CostModel modelOther = new ZeroCostModel();

        boolean equality = model.equals(modelSameFee);
        boolean inequality1 = model.equals(modelSameClass);
        boolean inequality2 = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality1);
        assertFalse(inequality2);
    }
}
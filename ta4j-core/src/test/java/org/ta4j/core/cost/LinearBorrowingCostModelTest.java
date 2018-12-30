package org.ta4j.core.cost;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

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
    public void calculateBuyTrade() {
        // Holding a bought stock should not incur borrowing costs
        int holdingPeriod = 2;
        Order entry = Order.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));
        Order exit = Order.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1));

        Trade trade = new Trade(entry, exit, new ZeroCostModel(), borrowingModel);

        Num costsFromTrade = trade.getHoldingCost();
        Num costsFromModel = borrowingModel.calculate(trade, holdingPeriod);

        assertNumEquals(costsFromModel, costsFromTrade);
        assertNumEquals(costsFromModel, DoubleNum.valueOf(0));
    }

    @Test
    public void calculateSellTrade() {
        // Short selling incurs borrowing costs
        int holdingPeriod = 2;
        Order entry = Order.sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));
        Order exit = Order.buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1));

        Trade trade = new Trade(entry, exit, new ZeroCostModel(), borrowingModel);

        Num costsFromTrade = trade.getHoldingCost();
        Num costsFromModel = borrowingModel.calculate(trade, holdingPeriod);

        assertNumEquals(costsFromModel, costsFromTrade);
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2));
    }

    @Test
    public void calculateOpenSellTrade() {
        // Short selling incurs borrowing costs. Since trade is still open, accounted for until current index
        int currentIndex = 4;
        Trade trade = new Trade(Order.OrderType.SELL, new ZeroCostModel(), borrowingModel);
        trade.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num costsFromTrade = trade.getHoldingCost(currentIndex);
        Num costsFromModel = borrowingModel.calculate(trade, currentIndex);

        assertNumEquals(costsFromModel, costsFromTrade);
        assertNumEquals(costsFromModel, DoubleNum.valueOf(4));
    }

    @Test
    public void testEquality() {
        LinearBorrowingCostModel model = new LinearBorrowingCostModel(0.1);
        CostModel modelSameClass = new LinearBorrowingCostModel(0.2);
        CostModel modelSameFee = new LinearBorrowingCostModel(0.1);
        CostModel modelOther = new ZeroCostModel();

        boolean equality = model.equals(modelSameFee);
        boolean inequality_1 = model.equals(modelSameClass);
        boolean inequality_2 = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality_1);
        assertFalse(inequality_2);
    }
}
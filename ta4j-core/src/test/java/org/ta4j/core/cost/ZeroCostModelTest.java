package org.ta4j.core.cost;

import org.junit.Test;
import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class ZeroCostModelTest {

    @Test
    public void calculatePerTrade() {
        // calculate costs per trade
        ZeroCostModel model = new ZeroCostModel();

        int holdingPeriod = 2;
        Order entry = Order.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), model);
        Order exit = Order.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), model);

        Trade trade = new Trade(entry, exit, model, model);
        Num cost = model.calculate(trade, holdingPeriod);

        assertNumEquals(cost, DoubleNum.valueOf(0));

    }

    @Test
    public void calculatePerPrice() {
        // calculate costs per trade
        ZeroCostModel model = new ZeroCostModel();
        Num cost = model.calculate(DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        assertNumEquals(cost, DoubleNum.valueOf(0));
    }

    @Test
    public void testEquality() {
        ZeroCostModel model = new ZeroCostModel();
        CostModel modelSame = new ZeroCostModel();
        CostModel modelOther = new LinearTransactionCostModel(0.1);
        boolean equality = model.equals(modelSame);
        boolean inequality = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality);
    }
}
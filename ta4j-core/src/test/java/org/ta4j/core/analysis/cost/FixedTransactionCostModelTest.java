/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Random;

import org.junit.Test;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class FixedTransactionCostModelTest {

    private static final Random RANDOM = new Random();

    private static final Num PRICE = DoubleNum.valueOf(100);

    private static final Num AMOUNT = DoubleNum.valueOf(5);

    @Test
    public void calculatePerPositionWhenPositionIsOpen() {
        double positionTrades = 1;
        double feePerTrade = RANDOM.nextDouble();
        FixedTransactionCostModel model = new FixedTransactionCostModel(feePerTrade);

        Position position = new Position(TradeType.BUY, model, null);
        position.operate(0, PRICE, AMOUNT);
        Num cost = model.calculate(position);

        assertNumEquals(cost, DoubleNum.valueOf(feePerTrade * positionTrades));
    }

    @Test
    public void calculatePerPositionWhenPositionIsClosed() {
        double positionTrades = 2;
        double feePerTrade = RANDOM.nextDouble();
        FixedTransactionCostModel model = new FixedTransactionCostModel(feePerTrade);

        int holdingPeriod = 2;
        Trade entry = Trade.buyAt(0, PRICE, AMOUNT, model);
        Trade exit = Trade.sellAt(holdingPeriod, PRICE, AMOUNT, model);

        Position position = new Position(entry, exit, model, model);
        Num cost = model.calculate(position, RANDOM.nextInt());

        assertNumEquals(cost, DoubleNum.valueOf(feePerTrade * positionTrades));
    }

    @Test
    public void calculatePerPrice() {
        double feePerTrade = RANDOM.nextDouble();
        FixedTransactionCostModel model = new FixedTransactionCostModel(feePerTrade);
        Num cost = model.calculate(PRICE, AMOUNT);

        assertNumEquals(cost, DoubleNum.valueOf(feePerTrade));
    }

    @Test
    public void testEquality() {
        double randomFee = RANDOM.nextDouble();
        FixedTransactionCostModel model = new FixedTransactionCostModel(randomFee);
        CostModel modelSame = new FixedTransactionCostModel(randomFee);
        CostModel modelOther = new LinearTransactionCostModel(randomFee);
        boolean equality = model.equals(modelSame);
        boolean inequality = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality);
    }
}

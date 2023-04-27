/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

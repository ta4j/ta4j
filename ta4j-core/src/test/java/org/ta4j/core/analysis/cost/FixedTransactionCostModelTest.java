/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
    final var positionTrades = 1;
    final var feePerTrade = RANDOM.nextDouble();
    final var model = new FixedTransactionCostModel(feePerTrade);

    final var position = new Position(TradeType.BUY, model, null);
    position.operate(0, PRICE, AMOUNT);
    final var cost = model.calculate(position);

    assertNumEquals(cost, DoubleNum.valueOf(feePerTrade * positionTrades));
  }


  @Test
  public void calculatePerPositionWhenPositionIsClosed() {
    final var positionTrades = 2;
    final var feePerTrade = RANDOM.nextDouble();
    final var model = new FixedTransactionCostModel(feePerTrade);

    final var holdingPeriod = 2;
    final var entry = Trade.buyAt(0, PRICE, AMOUNT, model);
    final var exit = Trade.sellAt(holdingPeriod, PRICE, AMOUNT, model);

    final var position = new Position(entry, exit, model, model);
    final var cost = model.calculate(position, RANDOM.nextInt());

    assertNumEquals(cost, DoubleNum.valueOf(feePerTrade * positionTrades));
  }


  @Test
  public void calculatePerPrice() {
    final double feePerTrade = RANDOM.nextDouble();
    final FixedTransactionCostModel model = new FixedTransactionCostModel(feePerTrade);
    final Num cost = model.calculate(PRICE, AMOUNT);

    assertNumEquals(cost, DoubleNum.valueOf(feePerTrade));
  }


  @Test
  public void testEquality() {
    final var randomFee = RANDOM.nextDouble();
    final var model = new FixedTransactionCostModel(randomFee);
    final var modelSame = new FixedTransactionCostModel(randomFee);
    final var modelOther = new LinearTransactionCostModel(randomFee);
    final var equality = model.equals(modelSame);
    final var inequality = model.equals(modelOther);

    assertTrue(equality);
    assertFalse(inequality);
  }
}

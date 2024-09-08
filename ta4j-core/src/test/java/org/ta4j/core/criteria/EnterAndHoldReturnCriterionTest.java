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
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.backtest.BackTestTradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class EnterAndHoldReturnCriterionTest extends AbstractCriterionTest {

  public EnterAndHoldReturnCriterionTest(final NumFactory numFactory) {
    super(
        params -> params.length == 0
                  ? EnterAndHoldReturnCriterion.buy()
                  : new EnterAndHoldReturnCriterion((TradeType) params[0]),
        numFactory
    );
  }


  @Test
  public void calculateWithEmpty() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(List.of()).build();
    final var tradingRecord = new BackTestTradingRecord();
    final var buyAndHold = getCriterion();
    final var sellAndHold = getCriterion(TradeType.SELL);

    final var buyAndHoldResult = buyAndHold.calculate(series, tradingRecord);
    final var sellAndHoldResult = sellAndHold.calculate(series, tradingRecord);

    assertNumEquals(1, buyAndHoldResult);
    assertNumEquals(1, sellAndHoldResult);

  }


  @Test
  public void calculateOnlyWithGainPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();
    final var tradingRecord = new BackTestTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
        Trade.buyAt(3, series), Trade.sellAt(5, series)
    );

    final var buyAndHold = getCriterion();
    assertNumEquals(1.05, buyAndHold.calculate(series, tradingRecord));

    final var sellAndHold = getCriterion(TradeType.SELL);
    assertNumEquals(0.95, sellAndHold.calculate(series, tradingRecord));
  }


  @Test
  public void calculateOnlyWithLossPositions() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 95, 100, 80, 85, 70).build();
    final var tradingRecord = new BackTestTradingRecord(
        Trade.buyAt(0, series),
        Trade.sellAt(1, series),
        Trade.buyAt(2, series),
        Trade.sellAt(5, series)
    );

    final var buyAndHold = getCriterion();
    assertNumEquals(0.7, buyAndHold.calculate(series, tradingRecord));

    final var sellAndHold = getCriterion(TradeType.SELL);
    assertNumEquals(1.3, sellAndHold.calculate(series, tradingRecord));
  }


  @Test
  public void calculateWithNoPositions() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 95, 100, 80, 85, 70).build();

    final var buyAndHold = getCriterion();
    assertNumEquals(1.0, buyAndHold.calculate(series, new BackTestTradingRecord()));

    final var sellAndHold = getCriterion(TradeType.SELL);
    assertNumEquals(1.0, sellAndHold.calculate(series, new BackTestTradingRecord()));
  }


  @Test
  public void calculateWithOnePositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 105).build();
    final var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
    final var buyAndHold = getCriterion();
    assertNumEquals(105d / 100, buyAndHold.calculate(series, position));

    final var sellAndHold = getCriterion(TradeType.SELL);
    assertNumEquals(0.95, sellAndHold.calculate(series, position));
  }


  @Test
  public void betterThan() {
    final var buyAndHold = getCriterion();
    assertTrue(buyAndHold.betterThan(numOf(1.3), numOf(1.1)));
    assertFalse(buyAndHold.betterThan(numOf(0.6), numOf(0.9)));

    final var sellAndHold = getCriterion(TradeType.SELL);
    assertTrue(sellAndHold.betterThan(numOf(1.3), numOf(1.1)));
    assertFalse(sellAndHold.betterThan(numOf(0.6), numOf(0.9)));
  }
}

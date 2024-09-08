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

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BackTestTradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class ExpectancyCriterionTest extends AbstractCriterionTest {

  public ExpectancyCriterionTest(final NumFactory numFactory) {
    super(params -> new ExpectancyCriterion(), numFactory);
  }


  @Test
  public void calculateOnlyWithProfitPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 110, 120, 130, 150, 160)
        .build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
        Trade.buyAt(3, series), Trade.sellAt(5, series)
    );

    final AnalysisCriterion avgLoss = getCriterion();
    assertNumEquals(1.0, avgLoss.calculate(series, tradingRecord));
  }


  @Test
  public void calculateWithMixedPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 110, 80, 130, 150, 160)
        .build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
        Trade.buyAt(3, series), Trade.sellAt(5, series)
    );

    final AnalysisCriterion avgLoss = getCriterion();
    assertNumEquals(0.25, avgLoss.calculate(series, tradingRecord));
  }


  @Test
  public void calculateOnlyWithLossPositions() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 95, 80, 70, 60, 50).build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
        Trade.buyAt(2, series), Trade.sellAt(5, series)
    );

    final AnalysisCriterion avgLoss = getCriterion();
    assertNumEquals(0, avgLoss.calculate(series, tradingRecord));
  }


  @Test
  public void calculateProfitWithShortPositions() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(160, 140, 120, 100, 80, 60).build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
        Trade.sellAt(2, series), Trade.buyAt(5, series)
    );

    final AnalysisCriterion avgLoss = getCriterion();
    assertNumEquals(1.0, avgLoss.calculate(series, tradingRecord));
  }


  @Test
  public void calculateProfitWithMixedShortPositions() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(160, 200, 120, 100, 80, 60).build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
        Trade.sellAt(2, series), Trade.buyAt(5, series)
    );

    final AnalysisCriterion avgLoss = getCriterion();
    assertNumEquals(0.25, avgLoss.calculate(series, tradingRecord));
  }


  @Test
  public void betterThan() {
    final AnalysisCriterion criterion = getCriterion();
    assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
    assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
  }


  @Test
  public void testCalculateOneOpenPositionShouldReturnZero() {
    this.openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(this.numFactory, getCriterion(), 0);
  }

}

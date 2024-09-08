/*
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
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BackTestTradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class NumberOfBarsCriterionTest extends AbstractCriterionTest {

  public NumberOfBarsCriterionTest(final NumFactory numFactory) {
    super(params -> new NumberOfBarsCriterion(), numFactory);
  }


  @Test
  public void calculateWithNoPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();

    final AnalysisCriterion numberOfBars = getCriterion();
    assertNumEquals(0, numberOfBars.calculate(series, new BackTestTradingRecord()));
  }


  @Test
  public void calculateWithTwoPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();
    final var tradingRecord = new BackTestTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
        Trade.buyAt(3, series), Trade.sellAt(5, series)
    );

    final AnalysisCriterion numberOfBars = getCriterion();
    assertNumEquals(6, numberOfBars.calculate(series, tradingRecord));
  }


  @Test
  public void calculateWithOnePosition() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 95, 100, 80, 85, 70).build();
    final var t = new Position(Trade.buyAt(2, series), Trade.sellAt(5, series));
    final var numberOfBars = getCriterion();
    assertNumEquals(4, numberOfBars.calculate(series, t));
  }


  @Test
  public void betterThan() {
    final var criterion = getCriterion();
    assertTrue(criterion.betterThan(numOf(3), numOf(6)));
    assertFalse(criterion.betterThan(numOf(6), numOf(2)));
  }


  @Test
  public void testCalculateOneOpenPositionShouldReturnZero() {
    this.openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(this.numFactory, getCriterion(), 0);
  }
}

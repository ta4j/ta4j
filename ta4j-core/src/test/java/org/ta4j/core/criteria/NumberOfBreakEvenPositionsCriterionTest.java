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
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BackTestTradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class NumberOfBreakEvenPositionsCriterionTest extends AbstractCriterionTest {

  public NumberOfBreakEvenPositionsCriterionTest(final NumFactory numFactory) {
    super(params -> new NumberOfBreakEvenPositionsCriterion(), numFactory);
  }


  @Test
  public void calculateWithNoPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();

    assertNumEquals(0, getCriterion().calculate(series, new BackTestTradingRecord()));
  }


  @Test
  public void calculateWithTwoLongPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.buyAt(0, series), Trade.sellAt(3, series),
        Trade.buyAt(1, series), Trade.sellAt(5, series)
    );

    assertNumEquals(2, getCriterion().calculate(series, tradingRecord));
  }


  @Test
  public void calculateWithOneLongPosition() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();
    final Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(3, series));

    assertNumEquals(1, getCriterion().calculate(series, position));
  }


  @Test
  public void calculateWithTwoShortPositions() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100, 105, 110, 100, 95, 105)
        .build();
    final TradingRecord tradingRecord = new BackTestTradingRecord(Trade.sellAt(0, series), Trade.buyAt(3, series),
        Trade.sellAt(1, series), Trade.buyAt(5, series)
    );

    assertNumEquals(2, getCriterion().calculate(series, tradingRecord));
  }


  @Test
  public void betterThan() {
    final AnalysisCriterion criterion = getCriterion();
    assertTrue(criterion.betterThan(numOf(3), numOf(6)));
    assertFalse(criterion.betterThan(numOf(7), numOf(4)));
  }


  @Test
  public void testCalculateOneOpenPositionShouldReturnZero() {
    this.openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(this.numFactory, getCriterion(), 0);
  }
}

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
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class AverageReturnPerBarCriterionTest extends AbstractCriterionTest {
  private BacktestBarSeries series;


  public AverageReturnPerBarCriterionTest(final NumFactory numFactory) {
    super(params -> new AverageReturnPerBarCriterion(), numFactory);
  }


  @Test
  public void calculateOnlyWithGainPositions() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100d, 105d, 110d, 100d, 95d, 105d)
        .build();
    final var tradingRecord =
        new BackTestTradingRecord(
            Trade.buyAt(0, this.series),
            Trade.sellAt(2, this.series),
            Trade.buyAt(3, this.series),
            Trade.sellAt(5, this.series)
        );
    final AnalysisCriterion averageProfit = getCriterion();
    assertNumEquals(1.0243, averageProfit.calculate(this.series, tradingRecord));
  }


  @Test
  public void calculateWithASimplePosition() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100d, 105d, 110d, 100d, 95d, 105d)
        .build();
    final var tradingRecord =
        new BackTestTradingRecord(Trade.buyAt(0, this.series), Trade.sellAt(2, this.series));
    final AnalysisCriterion averageProfit = getCriterion();
    assertNumEquals(numOf(110d / 100).pow(numOf(1d / 3)), averageProfit.calculate(this.series, tradingRecord));
  }


  @Test
  public void calculateOnlyWithLossPositions() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 95, 100, 80, 85, 70).build();
    final var tradingRecord =
        new BackTestTradingRecord(Trade.buyAt(0, this.series), Trade.sellAt(1, this.series),
            Trade.buyAt(2, this.series), Trade.sellAt(5, this.series)
        );
    final var averageProfit = getCriterion();
    assertNumEquals(
        numOf(95d / 100 * 70d / 100).pow(numOf(1d / 6)),
        averageProfit.calculate(this.series, tradingRecord)
    );
  }


  @Test
  public void calculateWithLosingAShortPositions() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(100d, 105d, 110d, 100d, 95d, 105d)
        .build();
    final TradingRecord tradingRecord =
        new BackTestTradingRecord(Trade.sellAt(0, this.series), Trade.buyAt(2, this.series));
    final var averageProfit = getCriterion();
    assertNumEquals(numOf(90d / 100).pow(numOf(1d / 3)), averageProfit.calculate(this.series, tradingRecord));
  }


  @Test
  public void calculateWithNoBarsShouldReturn1() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 95, 100, 80, 85, 70).build();
    final var averageProfit = getCriterion();
    assertNumEquals(1, averageProfit.calculate(this.series, new BackTestTradingRecord()));
  }


  @Test
  public void calculateWithOnePosition() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(100, 105).build();
    final var position = new Position(Trade.buyAt(0, this.series), Trade.sellAt(1, this.series));
    final var average = getCriterion();
    assertNumEquals(numOf(105d / 100).pow(numOf(0.5)), average.calculate(this.series, position));
  }


  @Test
  public void betterThan() {
    final var criterion = getCriterion();
    assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
    assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
  }
}

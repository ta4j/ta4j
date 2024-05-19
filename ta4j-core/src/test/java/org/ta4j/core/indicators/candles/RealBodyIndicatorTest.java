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
package org.ta4j.core.indicators.candles;

import static org.ta4j.core.TestUtils.assertNext;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RealBodyIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

  private BacktestBarSeries series;


  public RealBodyIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();
    this.series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(10).add();
    this.series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17).add();
    this.series.barBuilder().openPrice(15).closePrice(15).highPrice(16).lowPrice(14).add();
    this.series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(8).add();
    this.series.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).add();
  }


  @Test
  public void getValue() {
    final var body = new RealBodyIndicator(this.series);
    this.series.replaceStrategy(new MockStrategy(body));

    assertNext(this.series, 8, body);
    assertNext(this.series, 3, body);
    assertNext(this.series, 0, body);
    assertNext(this.series, -4, body);
    assertNext(this.series, 1, body);
  }
}

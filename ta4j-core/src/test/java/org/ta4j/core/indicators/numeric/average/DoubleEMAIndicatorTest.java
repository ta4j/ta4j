/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Lukáš Kvídera
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
package org.ta4j.core.indicators.numeric.average;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DoubleEMAIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public DoubleEMAIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(0.73, 0.72, 0.86, 0.72, 0.62, 0.76, 0.84, 0.69, 0.65, 0.71, 0.53, 0.73, 0.77, 0.67, 0.68)
        .build();
  }


  @Test
  public void doubleEMAUsingBarCount5UsingClosePrice() {
    final var doubleEma = new DoubleEMAIndicator(new ClosePriceIndicator(this.data), 5);
    this.data.replaceStrategy(new MockStrategy(doubleEma));

    this.data.advance();
    assertNumEquals(0.73, doubleEma.getValue());
    this.data.advance();
    assertNumEquals(0.7244, doubleEma.getValue());
    this.data.advance();
    assertNumEquals(0.7992, doubleEma.getValue());

    for (int i = 0; i < 6 - 2; i++) {
      this.data.advance();
    }
    assertNumEquals(0.7858, doubleEma.getValue());
    this.data.advance();
    assertNumEquals(0.7374, doubleEma.getValue());
    this.data.advance();
    assertNumEquals(0.6884, doubleEma.getValue());

    for (int i = 0; i < 12 - 8; i++) {
      this.data.advance();
    }
    assertNumEquals(0.7184, doubleEma.getValue());
    this.data.advance();
    assertNumEquals(0.6939, doubleEma.getValue());
    this.data.advance();
    assertNumEquals(0.6859, doubleEma.getValue());
  }
}

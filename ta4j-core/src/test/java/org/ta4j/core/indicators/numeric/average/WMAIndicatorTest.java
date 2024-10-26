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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WMAIndicatorTest extends AbstractIndicatorTest<Num> {

  public WMAIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Test
  public void calculate() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(1d, 2d, 3d, 4d, 5d, 6d).build();
    final var close = new ClosePriceIndicator(series);
    final var wmaIndicator = new WMAIndicator(close, 3);

    series.replaceStrategy(new MockStrategy(wmaIndicator));
    series.advance();
    assertFalse(wmaIndicator.isStable());
    series.advance();
    assertFalse(wmaIndicator.isStable());
    series.advance();
    assertTrue(wmaIndicator.isStable());
    assertNumEquals(2.3333, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(3.3333, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(4.3333, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(5.3333, wmaIndicator.getValue());
  }


  @Test
  public void wmaWithBarCountGreaterThanSeriesSize() {
    final var series =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(1d, 2d, 3d, 4d, 5d, 6d).build();
    final var close = new ClosePriceIndicator(series);
    final var wmaIndicator = new WMAIndicator(close, 55);
    final var strategy = new MockStrategy(wmaIndicator);
    series.replaceStrategy(strategy);

    for (int i = 0; i < 56; i++) {
      series.advance();
      assertFalse(String.valueOf(i), strategy.isStable());
    }
  }


  @Test
  public void wmaUsingBarCount9UsingClosePrice() {
    // Example from
    // http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98, 87.10, 83.11, 83.60, 83.66,
            82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87
        )
        .build();

    final var wmaIndicator = new WMAIndicator(new ClosePriceIndicator(series), 9);
    series.replaceStrategy(new MockStrategy(wmaIndicator));

    for (int i = 0; i < 9; i++) {
      series.advance();
    }

    assertNumEquals(84.4958, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(85.0158, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(84.6807, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(84.5387, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(84.4298, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(84.1224, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(83.1031, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(82.1462, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(81.1149, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(80.0736, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(78.6907, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(78.1504, wmaIndicator.getValue());
    series.advance();
    assertNumEquals(77.6133, wmaIndicator.getValue());
  }
}

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

public class HMAIndicatorTest extends AbstractIndicatorTest<Num> {

    private BacktestBarSeries data;

    public HMAIndicatorTest(final NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
      this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
                .withData(84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98, 87.10, 83.11, 83.60, 83.66,
                        82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87)
                .build();
    }

    @Test
    public void hmaUsingBarCount9UsingClosePrice() {
        // Example from
        // http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
        final var hma = new HMAIndicator(new ClosePriceIndicator(this.data), 9);

      this.data.replaceStrategy(new MockStrategy(hma));

      for (int i = 0; i < 11; i++) {
        this.data.advance();
      }

      assertNumEquals(86.3204, hma.getValue());
      this.data.advance();
      assertNumEquals(85.3705, hma.getValue());
      this.data.advance();
      assertNumEquals(84.1044, hma.getValue());
      this.data.advance();
      assertNumEquals(83.0197, hma.getValue());
      this.data.advance();
      assertNumEquals(81.3913, hma.getValue());
      this.data.advance();
      assertNumEquals(79.6511, hma.getValue());
      this.data.advance();
      assertNumEquals(78.0443, hma.getValue());
      this.data.advance();
      assertNumEquals(76.8832, hma.getValue());
      this.data.advance();
      assertNumEquals(75.5363, hma.getValue());
      this.data.advance();
      assertNumEquals(75.1713, hma.getValue());
      this.data.advance();
      assertNumEquals(75.3597, hma.getValue());
    }

}

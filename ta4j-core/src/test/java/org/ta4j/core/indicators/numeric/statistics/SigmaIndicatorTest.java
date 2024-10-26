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
package org.ta4j.core.indicators.numeric.statistics;

import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SigmaIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public SigmaIndicatorTest(final NumFactory numFunction) {
    super(numFunction);
  }


  @Before
  public void setUp() {
    this.data =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory)
            .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 8, 23, 9, 5, 1)
            .build();
  }


  @Test
  public void test() {
    final var zScore = NumericIndicator.closePrice(this.data).sigma(5);
    this.data.replaceStrategy(new MockStrategy(zScore));

    fastForward(this.data, 5);
    assertNext(this.data, 1.2649, zScore);
    assertNext(this.data, 1.2649, zScore);
    assertNext(this.data, 1.2649, zScore);
    assertNext(this.data, 1.2649, zScore);
    assertNext(this.data, 1.2649, zScore);
    assertNext(this.data, 1.2649, zScore);
    assertNext(this.data, 1.6696, zScore);
    assertNext(this.data, -0.686, zScore);
    assertNext(this.data, 1.6116, zScore);
    assertNext(this.data, -0.6447, zScore);
    assertNext(this.data, -0.9802, zScore);
    assertNext(this.data, -0.9857, zScore);
  }
}

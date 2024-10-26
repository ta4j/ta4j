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

import static org.ta4j.core.TestUtils.assertNext;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LWMAIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public LWMAIndicatorTest(final NumFactory numFunction) {
    super(numFunction);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79, 36.83)
        .build();
  }


  @Test
  public void lwmaUsingBarCount5UsingClosePrice() {
    final var lwma = NumericIndicator.closePrice(data).lwma(5);
    this.data.replaceStrategy(new MockStrategy(lwma));

    assertNext(this.data,0.0, lwma);
    assertNext(this.data,0.0, lwma);
    assertNext(this.data,0.0, lwma);
    assertNext(this.data,0.0, lwma);
    assertNext(this.data,36.0506, lwma);
    assertNext(this.data,35.9673, lwma);
    assertNext(this.data,36.0766, lwma);
    assertNext(this.data,36.6253, lwma);
    assertNext(this.data,37.1833, lwma);
    assertNext(this.data,37.5240, lwma);
    assertNext(this.data,37.4060, lwma);
  }
}

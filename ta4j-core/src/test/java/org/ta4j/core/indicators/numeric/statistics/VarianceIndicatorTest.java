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
package org.ta4j.core.indicators.numeric.statistics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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

public class VarianceIndicatorTest extends AbstractIndicatorTest<Num> {
  private BacktestBarSeries data;


  public VarianceIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9).build();
  }


  @Test
  public void varianceUsingBarCount4UsingClosePrice() {
    final var varianceIndicator = NumericIndicator.closePrice(this.data).variance(4);
    this.data.replaceStrategy(new MockStrategy(varianceIndicator));

    // unstable values may produce garbage, this is why they are called unstable
    assertNext(this.data, 0.0, varianceIndicator);
    assertFalse(varianceIndicator.isStable());

    assertNext(this.data, 0.1667, varianceIndicator);
    assertFalse(varianceIndicator.isStable());

    assertNext(this.data, 0.6667, varianceIndicator);
    assertFalse(varianceIndicator.isStable());

    // stable date bellow
    assertNext(this.data, 1.6667, varianceIndicator);
    assertTrue(varianceIndicator.isStable());

    assertNext(this.data, 0.6667, varianceIndicator);
    assertTrue(varianceIndicator.isStable());

    assertNext(this.data, 0.3333, varianceIndicator);
    assertNext(this.data, 0.6667, varianceIndicator);
    assertNext(this.data, 0.6667, varianceIndicator);
    assertNext(this.data, 0.6667, varianceIndicator);
    assertNext(this.data, 4.6667, varianceIndicator);
    assertNext(this.data, 14.000, varianceIndicator);
  }


  @Test(expected = IllegalArgumentException.class)
  public void varianceShouldBeZeroWhenBarCountIs1() {
    NumericIndicator.closePrice(this.data).variance(1);
  }


  @Test
  public void varianceUsingBarCount2UsingClosePrice() {
    final var variance = NumericIndicator.closePrice(this.data).variance(2);
    this.data.replaceStrategy(new MockStrategy(variance));

    assertNext(this.data, 0.0, variance);
    assertFalse(variance.isStable());

    assertNext(this.data, 0.5, variance);
    assertTrue(variance.isStable());

    assertNext(this.data, 0.5, variance);
    assertTrue(variance.isStable());

    assertNext(this.data, 0.5, variance);
    assertTrue(variance.isStable());

    assertNext(this.data, 0.5, variance);
    assertTrue(variance.isStable());

    assertNext(this.data, 0.5, variance);
    assertTrue(variance.isStable());

    assertNext(this.data, 0.5, variance);
    assertNext(this.data, 0.5, variance);
    assertNext(this.data, 0.5, variance);
    assertNext(this.data, 4.5, variance);
    assertNext(this.data, 40.5, variance);
  }
}

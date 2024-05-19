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
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNext;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
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
    final var var = new VarianceIndicator(new ClosePriceIndicator(this.data), 4);
    this.data.replaceStrategy(new MockStrategy(var));

    // unstable values may produce garbage, this is why they are called unstable
    assertNext(this.data, 0.0, var);
    assertFalse(var.isStable());

    assertNext(this.data, 0.0, var);
    assertFalse(var.isStable());

    assertNext(this.data, 0.0, var);
    assertFalse(var.isStable());

    // stable date bellow
    assertNext(this.data, 1.6667, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.6667, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.3333, var);
    assertNext(this.data, 0.6667, var);
    assertNext(this.data, 0.6667, var);
    assertNext(this.data, 0.6667, var);
    assertNext(this.data, 4.6667, var);
    assertNext(this.data, 14.000, var);
  }


  @Test(expected = IllegalArgumentException.class)
  public void varianceShouldBeZeroWhenBarCountIs1() {
    new VarianceIndicator(new ClosePriceIndicator(this.data), 1);
  }


  @Test
  public void varianceUsingBarCount2UsingClosePrice() {
    final var var = new VarianceIndicator(new ClosePriceIndicator(this.data), 2);
    this.data.replaceStrategy(new MockStrategy(var));

    assertNext(this.data, 0.0, var);
    assertFalse(var.isStable());

    assertNext(this.data, 0.5, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.5, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.5, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.5, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.5, var);
    assertTrue(var.isStable());

    assertNext(this.data, 0.5, var);
    assertNext(this.data, 0.5, var);
    assertNext(this.data, 0.5, var);
    assertNext(this.data, 4.5, var);
  }
}

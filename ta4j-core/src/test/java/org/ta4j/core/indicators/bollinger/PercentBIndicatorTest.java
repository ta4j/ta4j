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
package org.ta4j.core.indicators.bollinger;

import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PercentBIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public PercentBIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(10, 12, 15, 14, 17, 20, 21, 20, 20, 19, 20, 17, 12, 12, 9, 8, 9, 10, 9, 10)
        .build();
  }


  @Test
  public void percentBUsingSMAAndStandardDeviation() {

    final var pcb = new PercentBIndicator(new ClosePriceIndicator(this.data), 5, 2);
    this.data.replaceStrategy(new MockStrategy(pcb));

    fastForward(this.data, 5);
    assertNext(this.data, 0.8146, pcb);
    assertNext(this.data, 0.8607, pcb);
    assertNext(this.data, 0.7951, pcb);
    assertNext(this.data, 0.6388, pcb);
    assertNext(this.data, 0.5659, pcb);
    assertNext(this.data, 0.1464, pcb);
    assertNext(this.data, 0.5000, pcb);
    assertNext(this.data, 0.0782, pcb);
    assertNext(this.data, 0.0835, pcb);
    assertNext(this.data, 0.2374, pcb);
    assertNext(this.data, 0.2169, pcb);
    assertNext(this.data, 0.2434, pcb);
    assertNext(this.data, 0.3664, pcb);
    assertNext(this.data, 0.5659, pcb);
    assertNext(this.data, 0.5000, pcb);
    assertNext(this.data, 0.7391, pcb);
  }
}

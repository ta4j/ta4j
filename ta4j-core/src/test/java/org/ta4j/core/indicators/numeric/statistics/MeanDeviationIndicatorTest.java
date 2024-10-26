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
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MeanDeviationIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public MeanDeviationIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data =
        new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9).build();
  }


  @Test
  public void meanDeviationUsingBarCount5UsingClosePrice() {
    final var meanDeviation = NumericIndicator.closePrice(data).meanDeviation(5);
    this.data.replaceStrategy(new MockStrategy(meanDeviation));

    fastForward(this.data, 3);
    assertNext(this.data, 2.44444444444444, meanDeviation);
    assertNext(this.data, 2.5, meanDeviation);
    assertNext(this.data, 2.16, meanDeviation);
    assertNext(this.data, 1.68, meanDeviation);
    assertNext(this.data, 1.2, meanDeviation);
  }


  @Test
  public void firstValueShouldBeZero() {
    final var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(this.data), 5);
    this.data.replaceStrategy(new MockStrategy(meanDeviation));

    assertNext(this.data, 0, meanDeviation);
  }


  @Test
  public void meanDeviationShouldBeZeroWhenBarCountIs1() {
    final var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(this.data), 1);
    this.data.replaceStrategy(new MockStrategy(meanDeviation));

    assertNext(this.data, 0, meanDeviation);
    assertNext(this.data, 0, meanDeviation);
  }
}

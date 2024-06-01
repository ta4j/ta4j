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
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;
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

public class DifferenceIndicatorTest extends AbstractIndicatorTest<Num> {

  private DifferenceIndicator closePriceDifference;

  private BacktestBarSeries barSeries;


  public DifferenceIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.barSeries = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withDefaultData().build();
    this.closePriceDifference = new DifferenceIndicator(new ClosePriceIndicator(this.barSeries));
  }


  @Test
  public void indicatorShouldRetrieveBarDifference() {
    this.barSeries.replaceStrategy(new MockStrategy(this.closePriceDifference));
    this.barSeries.advance();
    assertNumEquals(1.0, this.closePriceDifference.getValue());
    for (int i = 1; i < 10; i++) {
      this.barSeries.advance();
      final Num previousBarClosePrice = this.barSeries.getBar(i - 1).closePrice();
      final Num currentBarClosePrice = this.barSeries.getBar(i).closePrice();
      assertEquals(currentBarClosePrice.minus(previousBarClosePrice), this.closePriceDifference.getValue());
    }
  }
}

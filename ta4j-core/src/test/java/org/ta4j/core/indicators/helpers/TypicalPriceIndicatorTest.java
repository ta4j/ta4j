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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TypicalPriceIndicatorTest extends AbstractIndicatorTest<Num> {

  private TypicalPriceIndicator typicalPriceIndicator;

  private BacktestBarSeries barSeries;


  public TypicalPriceIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.barSeries = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withDefaultData().build();
    this.typicalPriceIndicator = new TypicalPriceIndicator(this.barSeries);
  }


  @Test
  public void indicatorShouldRetrieveBarHighPrice() {
    this.barSeries.replaceStrategy(new MockStrategy(this.typicalPriceIndicator));

    while (this.barSeries.advance()) {
      final Bar bar = this.barSeries.getBar();
      final Num typicalPrice = bar.highPrice().plus(bar.lowPrice()).plus(bar.closePrice()).dividedBy(numOf(3));
      assertEquals(typicalPrice, this.typicalPriceIndicator.getValue());
    }
  }
}

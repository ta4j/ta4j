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

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class HighestValueIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public HighestValueIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2)
        .build();
  }


  @Test
  public void highestValueUsingBarCount5UsingClosePrice() {
    final var highestValue = new HighestValueIndicator(new ClosePriceIndicator(this.data), 5);
    this.data.replaceStrategy(new MockStrategy(highestValue));

    for (int i = 0; i < 5; i++) {
      this.data.advance();
    }
    assertNumEquals("4.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("4.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("5.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("6.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("6.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("6.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("6.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("6.0", highestValue.getValue());
    this.data.advance();
    assertNumEquals("4.0", highestValue.getValue());
  }


  @Test
  public void firstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
    final var highestValue = new HighestValueIndicator(new ClosePriceIndicator(this.data), 5);
    this.data.replaceStrategy(new MockStrategy(highestValue));
    this.data.advance();

    assertNumEquals("1.0", highestValue.getValue());
  }


  @Test
  public void highestValueIndicatorWhenBarCountIsGreaterThanIndex() {
    final var highestValue = new HighestValueIndicator(new ClosePriceIndicator(this.data), 500);
    this.data.replaceStrategy(new MockStrategy(highestValue));
    for (int i = 0; i < 12; i++) {
      this.data.advance();
    }

    assertNumEquals("6.0", highestValue.getValue());
  }


  @Test
  public void onlyNaNValues() {
    final var series = new MockBarSeriesBuilder().withName("NaN test").build();
    for (long i = 0; i <= 10000; i++) {
      series.barBuilder()
          .openPrice(NaN)
          .closePrice(NaN)
          .highPrice(NaN)
          .lowPrice(NaN)
          .volume(NaN)
          .add();
    }

    final var highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 5);
    series.replaceStrategy(new MockStrategy(highestValue));

    while (series.advance()) {
      assertEquals(NaN.toString(), highestValue.getValue().toString());
    }
  }


  @Test
  public void naNValuesInIntervall() {
    final BacktestBarSeries series = new MockBarSeriesBuilder().withName("NaN test").build();
    for (long i = 0; i <= 10; i++) { // (0, NaN, 2, NaN, 3, NaN, 4, NaN, 5, ...)
      final Num closePrice = i % 2 == 0 ? series.numFactory().numOf(i) : NaN;
      series.barBuilder()
          .openPrice(NaN)
          .closePrice(closePrice)
          .highPrice(NaN)
          .lowPrice(NaN)
          .volume(NaN)
          .add();
    }

    final var highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 2);
    series.replaceStrategy(new MockStrategy(highestValue));

    // index is the biggest of (index, index-1)
    for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
      series.advance();
      if (i % 2 != 0) // current is NaN take the previous as highest
      {
        assertEquals(series.getBar(i - 1).closePrice().toString(), highestValue.getValue().toString());
      } else // current is not NaN but previous, take the current
      {
        assertEquals(series.getBar(i).closePrice().toString(), highestValue.getValue().toString());
      }
    }
  }
}

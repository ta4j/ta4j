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

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMAIndicatorTest extends AbstractIndicatorTest<Num> {

  private final ExternalIndicatorTest xls;


  public SMAIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
    this.xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6, numFactory);
  }


  private BacktestBarSeries data;


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
        .build();
  }


  @Test
  public void usingBarCount3UsingClosePrice() {
    final var indicator = NumericIndicator.closePrice(this.data).sma(3);
    this.data.addStrategy(new MockStrategy(indicator));

    this.data.advance();
    assertNumEquals((0d + 0d + 1d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((0d + 1d + 2d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((1d + 2d + 3d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(3, indicator.getValue());
    this.data.advance();
    assertNumEquals(10d / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(11d / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(4, indicator.getValue());
    this.data.advance();
    assertNumEquals(13d / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(4, indicator.getValue());
    this.data.advance();
    assertNumEquals(10d / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(10d / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(10d / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals(3, indicator.getValue());
  }


  @Test
  public void usingBarCount3UsingClosePriceMovingSerie() {
    this.data.barBuilder().closePrice(5.).add();

    final var indicator = NumericIndicator.closePrice(this.data).sma(3);
    this.data.addStrategy(new MockStrategy(indicator));
    // unstable bars skipped, unpredictable results

    this.data.advance();
    assertNumEquals((0d + 0d + 1d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((0d + 1d + 2d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((1d + 2d + 3d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((2d + 3d + 4d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((3d + 4d + 3d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((4d + 3d + 4d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((3d + 4d + 5d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((4d + 5d + 4d) / 3, indicator.getValue());
    this.data.advance();
    assertNumEquals((5d + 4d + 3d) / 3, indicator.getValue());
  }


  @Test
  public void whenBarCountIs1ResultShouldBeIndicatorValue() {
    final var indicator = NumericIndicator.closePrice(this.data).sma(1);
    this.data.addStrategy(new MockStrategy(indicator));

    while (this.data.advance()) {
      assertEquals(this.data.getBar().closePrice(), indicator.getValue());
    }
  }


  @Test
  public void externalData3() throws Exception {
    final var series = this.xls.getSeries();

    final var actualIndicator = NumericIndicator.closePrice(series).sma(3);
    final var expectedIndicator = this.xls.getIndicator(3);
    series.addStrategy(new MockStrategy(actualIndicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(series, actualIndicator));
    assertEquals(326.6333, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void externalData1() throws Exception {
    final var series = this.xls.getSeries();

    final var actualIndicator = NumericIndicator.closePrice(series).sma(1);
    final var expectedIndicator = this.xls.getIndicator(1);

    series.addStrategy(new MockStrategy(actualIndicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(series, actualIndicator));
    assertEquals(329.0, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void externalData13() throws Exception {
    final var series = this.xls.getSeries();
    final Indicator<Num> xlsClose = new ClosePriceIndicator(series);
    series.addStrategy(new MockStrategy(xlsClose));

    final var actualIndicator = NumericIndicator.closePrice(series).sma(13);
    final var expectedIndicator = this.xls.getIndicator(13);
    series.addStrategy(new MockStrategy(actualIndicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(series, actualIndicator));
    assertEquals(327.7846, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }

}

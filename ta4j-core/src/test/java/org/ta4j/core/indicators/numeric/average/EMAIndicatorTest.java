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
import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EMAIndicatorTest extends AbstractIndicatorTest<Num> {

  private final ExternalIndicatorTest xls;


  public EMAIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
    this.xls = new XLSIndicatorTest(this.getClass(), "EMA.xls", 6, numFactory);
  }


  private BacktestBarSeries data;


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95, 63.37, 61.33, 61.51)
        .build();
  }


  @Test
  public void firstValueShouldBeEqualsToFirstDataValue() {
    final var indicator = NumericIndicator.closePrice(this.data).ema(1);
    this.data.replaceStrategy(new MockStrategy(indicator));

    assertNext(this.data, 64.75, indicator);
  }


  @Test
  public void usingBarCount10UsingClosePrice() {
    final var indicator = NumericIndicator.closePrice(this.data).ema(10);
    this.data.replaceStrategy(new MockStrategy(indicator));

    fastForward(this.data, 10);
    assertNext(this.data, 63.6948, indicator);
    assertNext(this.data, 63.2648, indicator);
    assertNext(this.data, 62.9457, indicator);
  }


  @Test
  public void externalData1() throws Exception {
    final BacktestBarSeries xlsSeries = this.xls.getSeries();
    final var indicator = NumericIndicator.closePrice(xlsSeries).ema(1);
    final var expectedIndicator = this.xls.getIndicator(1);
    xlsSeries.replaceStrategy(new MockStrategy(indicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(xlsSeries, indicator));
    assertEquals(329.0, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void externalData3() throws Exception {
    final BacktestBarSeries xlsSeries = this.xls.getSeries();
    final var indicator = NumericIndicator.closePrice(xlsSeries).ema(3);
    final var expectedIndicator = this.xls.getIndicator(3);
    xlsSeries.replaceStrategy(new MockStrategy(indicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(xlsSeries, indicator));
    assertEquals(327.7748, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void externalData13() throws Exception {
    final BacktestBarSeries xlsSeries = this.xls.getSeries();
    final var indicator = NumericIndicator.closePrice(xlsSeries).ema(13);
    final var expectedIndicator = this.xls.getIndicator(13);
    xlsSeries.replaceStrategy(new MockStrategy(indicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(xlsSeries, indicator));
    assertEquals(327.4076, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }

}

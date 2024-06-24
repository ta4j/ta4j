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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RSIIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;
  private final ExternalIndicatorTest xls;


  public RSIIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
    this.xls = new XLSIndicatorTest(this.getClass(), "RSI.xls", 10, numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(50.45, 50.30, 50.20, 50.15, 50.05, 50.06, 50.10, 50.08, 50.03, 50.07, 50.01, 50.14, 50.22,
            50.43, 50.50, 50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86, 51.20, 51.30, 51.10
        )
        .build();
  }


  @Test
  public void firstValueShouldBeZero() {
    final var indicator = NumericIndicator.closePrice(this.data).rsi(14);
    this.data.replaceStrategy(new MockStrategy(indicator));

    assertEquals(this.numFactory.zero(), indicator.getValue());
  }


  @Test
  public void hundredIfNoLoss() {
    final var indicator = NumericIndicator.closePrice(this.data).rsi(1);
    this.data.replaceStrategy(new MockStrategy(indicator));

    fastForward(this.data, 15);
    assertNext(this.data, this.numFactory.hundred().doubleValue(), indicator);
    assertNext(this.data, this.numFactory.hundred().doubleValue(), indicator);
  }


  @Test
  public void zeroIfNoGain() {
    final var indicator = NumericIndicator.closePrice(this.data).rsi(1);
    this.data.replaceStrategy(new MockStrategy(indicator));

    fastForward(this.data, 2);
    assertNext(this.data, this.numFactory.zero().doubleValue(), indicator);
    assertNext(this.data, this.numFactory.zero().doubleValue(), indicator);
  }


  @Test
  public void usingBarCount14UsingClosePrice() {
    final var indicator = NumericIndicator.closePrice(this.data).rsi(14);
    this.data.replaceStrategy(new MockStrategy(indicator));

    fastForward(this.data, 16);
    assertNext(this.data, 68.4746, indicator);
    assertNext(this.data, 64.7836, indicator);
    assertNext(this.data, 72.0776, indicator);
    assertNext(this.data, 60.7800, indicator);
    assertNext(this.data, 63.6439, indicator);
    assertNext(this.data, 72.3433, indicator);
    assertNext(this.data, 67.3822, indicator);
    assertNext(this.data, 68.5438, indicator);
    assertNext(this.data, 76.2770, indicator);
    assertNext(this.data, 77.9908, indicator);
    assertNext(this.data, 67.4895, indicator);
  }


  @Test
  public void xlsTest() throws Exception {
    final var xlsSeries = this.xls.getSeries();
    final var xlsClose = NumericIndicator.closePrice(xlsSeries);
    xlsSeries.replaceStrategy(new MockStrategy(xlsClose));
    Indicator<Num> indicator;

    indicator = xlsClose.rsi(1);
    assertIndicatorEquals(this.xls.getIndicator(1), new TestIndicator<>(xlsSeries, indicator));

    indicator = xlsClose.rsi(3);
    assertIndicatorEquals(this.xls.getIndicator(3), new TestIndicator<>(xlsSeries, indicator));

    indicator = xlsClose.rsi(13);
    assertIndicatorEquals(this.xls.getIndicator(13), new TestIndicator<>(xlsSeries, indicator));
  }
}

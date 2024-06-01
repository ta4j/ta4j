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
import org.ta4j.core.indicators.average.SMAIndicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BollingerBandWidthIndicatorTest extends AbstractIndicatorTest<Num> {

  private ClosePriceIndicator closePrice;
  private BacktestBarSeries data;


  public BollingerBandWidthIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(10, 12, 15, 14, 17, 20, 21, 20, 20, 19, 20, 17, 12, 12, 9, 8, 9, 10, 9, 10)
        .build();
    this.closePrice = new ClosePriceIndicator(this.data);
  }


  @Test
  public void bollingerBandWidthUsingSMAAndStandardDeviation() {

    final var sma = new SMAIndicator(this.closePrice, 5);
    final var standardDeviation = new StandardDeviationIndicator(this.closePrice, 5);

    final var bbmSMA = new BollingerBandsMiddleIndicator(sma);
    final var bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);
    final var bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

    final var bandwidth = new BollingerBandWidthIndicator(bbuSMA, bbmSMA, bblSMA);

    this.data.replaceStrategy(new MockStrategy(bandwidth, bbuSMA, bbmSMA, bblSMA, standardDeviation, sma));

    fastForward(this.data, 5);
    assertNext(this.data, 79.4662, bandwidth);
    assertNext(this.data, 78.1946, bandwidth);
    assertNext(this.data, 70.1055, bandwidth);
    assertNext(this.data, 62.6298, bandwidth);
    assertNext(this.data, 30.9505, bandwidth);
    assertNext(this.data, 14.1421, bandwidth);
    assertNext(this.data, 14.1421, bandwidth);
    assertNext(this.data, 27.1633, bandwidth);
    assertNext(this.data, 76.3989, bandwidth);
    assertNext(this.data, 95.1971, bandwidth);
    assertNext(this.data, 126.1680, bandwidth);
    assertNext(this.data, 120.9357, bandwidth);
    assertNext(this.data, 74.8331, bandwidth);
    assertNext(this.data, 63.1906, bandwidth);
    assertNext(this.data, 31.4270, bandwidth);
    assertNext(this.data, 36.3766, bandwidth);
  }
}

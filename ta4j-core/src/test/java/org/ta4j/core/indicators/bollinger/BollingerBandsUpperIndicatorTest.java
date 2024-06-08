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

public class BollingerBandsUpperIndicatorTest extends AbstractIndicatorTest<Num> {

  private int barCount;

  private ClosePriceIndicator closePrice;

  private SMAIndicator sma;
  private BacktestBarSeries data;


  public BollingerBandsUpperIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
        .build();
    this.barCount = 3;
    this.closePrice = new ClosePriceIndicator(this.data);
    this.sma = new SMAIndicator(this.closePrice, this.barCount);
  }


  @Test
  public void bollingerBandsUpperUsingSMAAndStandardDeviation() {

    final var bbmSMA = new BollingerBandsMiddleIndicator(this.sma);
    final var standardDeviation = new StandardDeviationIndicator(this.closePrice, this.barCount);
    final var bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);
    this.data.replaceStrategy(new MockStrategy(bbuSMA, bbmSMA, standardDeviation));

    fastForward(this.data, 3);
    assertNext(this.data, 4.0000, bbuSMA);
    assertNext(this.data, 5.0000, bbuSMA);
    assertNext(this.data, 4.4880, bbuSMA);
    assertNext(this.data, 4.8213, bbuSMA);
    assertNext(this.data, 6.0000, bbuSMA);
    assertNext(this.data, 5.4880, bbuSMA);
    assertNext(this.data, 6.0000, bbuSMA);
    assertNext(this.data, 4.4880, bbuSMA);
    assertNext(this.data, 4.4880, bbuSMA);
    assertNext(this.data, 4.4880, bbuSMA);
    assertNext(this.data, 5.0000, bbuSMA);
  }


  @Test
  public void bollingerBandsUpperUsingSMAAndStandardDeviationWithK() {
    final var bbmSMA = new BollingerBandsMiddleIndicator(this.sma);
    final var standardDeviation = new StandardDeviationIndicator(this.closePrice, this.barCount);
    final var bbuSMAwithK = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation, this.numFactory.numOf(1.5));
    this.data.replaceStrategy(new MockStrategy(bbmSMA, bbuSMAwithK, standardDeviation));

    fastForward(this.data, 3);
    assertNext(this.data, 3.5000, bbuSMAwithK);
    assertNext(this.data, 4.5000, bbuSMAwithK);
    assertNext(this.data, 4.1993, bbuSMAwithK);
    assertNext(this.data, 4.5327, bbuSMAwithK);
    assertNext(this.data, 5.5000, bbuSMAwithK);
    assertNext(this.data, 5.1994, bbuSMAwithK);
    assertNext(this.data, 5.5000, bbuSMAwithK);
    assertNext(this.data, 4.1994, bbuSMAwithK);
    assertNext(this.data, 4.1994, bbuSMAwithK);
    assertNext(this.data, 4.1994, bbuSMAwithK);
    assertNext(this.data, 4.5000, bbuSMAwithK);
  }
}

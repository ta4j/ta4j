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
package org.ta4j.core.indicators.candles;

import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SeriesRelatedBooleanIndicator;
import org.ta4j.core.num.Num;

/**
 * Bullish engulfing pattern indicator.
 *
 * @see <a href=
 *     "http://www.investopedia.com/terms/b/bullishengulfingpattern.asp">
 *     http://www.investopedia.com/terms/b/bullishengulfingpattern.asp</a>
 */
public class BullishEngulfingIndicator extends SeriesRelatedBooleanIndicator {

  private Instant currentTick = Instant.EPOCH;
  private Boolean value;
  private Bar previousBar;


  /**
   * Constructor.
   *
   * @param series the bar series
   */
  public BullishEngulfingIndicator(final BarSeries series) {
    super(series);
  }


  protected Boolean calculate() {
    if (this.value == null) {
      this.previousBar = getBarSeries().getBar();
      // Engulfing is a 2-candle pattern
      return false;
    }

    final Bar prevBar = this.previousBar;
    final Bar currBar = getBarSeries().getBar();
    this.previousBar = currBar;

    if (prevBar.isBearish() && currBar.isBullish()) {
      final Num prevOpenPrice = prevBar.openPrice();
      final Num prevClosePrice = prevBar.closePrice();
      final Num currOpenPrice = currBar.openPrice();
      final Num currClosePrice = currBar.closePrice();
      return currOpenPrice.isLessThan(prevOpenPrice)
             && currOpenPrice.isLessThan(prevClosePrice)
             && currClosePrice.isGreaterThan(prevOpenPrice)
             && currClosePrice.isGreaterThan(prevClosePrice);
    }

    return false;
  }


  @Override
  public Boolean getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.value != null;
  }
}

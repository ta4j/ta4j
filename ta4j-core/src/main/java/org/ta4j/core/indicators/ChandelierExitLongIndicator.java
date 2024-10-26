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

import java.time.Instant;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.bool.BooleanIndicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * The Chandelier Exit (long) Indicator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit</a>
 */
public class ChandelierExitLongIndicator extends BooleanIndicator {

  private final HighestValueIndicator high;
  private final ATRIndicator atr;
  private final Num k;
  private final ClosePriceIndicator close;
  private Instant currentTick = Instant.EPOCH;
  private boolean value;


  /**
   * Constructor with:
   *
   * <ul>
   * <li>{@code barCount} = 22
   * <li>{@code k} = 3
   * </ul>
   *
   * @param series the bar series
   */
  public ChandelierExitLongIndicator(final BarSeries series) {
    this(series, 22, 3);
  }


  /**
   * Constructor.
   *
   * @param series the bar series
   * @param barCount the time frame (usually 22)
   * @param k the K multiplier for ATR (usually 3.0)
   */
  public ChandelierExitLongIndicator(final BarSeries series, final int barCount, final double k) {
    this.close = NumericIndicator.closePrice(series);
    this.high = NumericIndicator.highPrice(series).highest(barCount);
    this.atr = NumericIndicator.atr(series, barCount);
    this.k = series.numFactory().numOf(k);
  }


  private boolean calculate() {
    return this.close.isLessThan(this.high.getValue().minus(this.atr.getValue().multipliedBy(this.k)).doubleValue());
  }


  @Override
  public Boolean getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.close.refresh(tick);
      this.high.refresh(tick);
      this.atr.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.close.isStable() && this.high.isStable() && this.atr.isStable();
  }
}

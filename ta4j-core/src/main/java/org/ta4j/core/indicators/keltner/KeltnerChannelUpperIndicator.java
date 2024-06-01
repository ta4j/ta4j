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
package org.ta4j.core.indicators.keltner;

import java.time.Instant;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Keltner Channel (upper line) indicator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelUpperIndicator extends NumericIndicator {

  private final ATRIndicator averageTrueRangeIndicator;
  private final KeltnerChannelMiddleIndicator keltnerMiddleIndicator;
  private final Num ratio;
  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Constructor.
   *
   * @param middle the {@link #keltnerMiddleIndicator}
   * @param ratio the {@link #ratio}
   * @param barCountATR the bar count for the {@link ATRIndicator}
   */
  public KeltnerChannelUpperIndicator(
      final BarSeries series,
      final KeltnerChannelMiddleIndicator middle,
      final double ratio,
      final int barCountATR
  ) {
    this(series, middle, new ATRIndicator(series, barCountATR), ratio);
  }


  /**
   * Constructor.
   *
   * @param middle the {@link #keltnerMiddleIndicator}
   * @param atr the {@link ATRIndicator}
   * @param ratio the {@link #ratio}
   */
  public KeltnerChannelUpperIndicator(
      final BarSeries series,
      final KeltnerChannelMiddleIndicator middle,
      final ATRIndicator atr,
      final double ratio
  ) {
    super(series.numFactory());
    this.keltnerMiddleIndicator = middle;
    this.averageTrueRangeIndicator = atr;
    this.ratio = getNumFactory().numOf(ratio);
  }


  protected Num calculate() {
    return this.keltnerMiddleIndicator.getValue()
        .plus(this.ratio.multipliedBy(this.averageTrueRangeIndicator.getValue()));
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.keltnerMiddleIndicator.refresh(tick);
      this.averageTrueRangeIndicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.keltnerMiddleIndicator.isStable() && this.averageTrueRangeIndicator.isStable();
  }
}

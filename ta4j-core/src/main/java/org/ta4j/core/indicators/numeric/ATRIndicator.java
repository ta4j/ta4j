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
package org.ta4j.core.indicators.numeric;

import java.time.Instant;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.numeric.average.MMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Average true range indicator.
 */
public class ATRIndicator extends NumericIndicator {

  private final MMAIndicator averageTrueRangeIndicator;


  /**
   * Constructor.
   *
   * @param series the bar series
   * @param barCount the time frame
   */
  public ATRIndicator(final BarSeries series, final int barCount) {
    this(series, new TRIndicator(series), barCount);
  }


  /**
   * Constructor.
   *
   * @param series the series
   * @param tr the {@link TRIndicator}
   * @param barCount the time frame
   */
  public ATRIndicator(final BarSeries series, final NumericIndicator tr, final int barCount) {
    super(series.numFactory());
    this.averageTrueRangeIndicator = new MMAIndicator(tr, barCount);
  }


  @Override
  public Num getValue() {
    return this.averageTrueRangeIndicator.getValue();
  }


  @Override
  public void refresh(final Instant tick) {
    this.averageTrueRangeIndicator.refresh(tick);
  }


  @Override
  public boolean isStable() {
    return this.averageTrueRangeIndicator.isStable();
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " " + this.averageTrueRangeIndicator;
  }
}

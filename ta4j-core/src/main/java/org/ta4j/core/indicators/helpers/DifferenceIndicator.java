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

import java.time.Instant;

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Calculates the difference between the current and the previous indicator value.
 *
 * <pre>
 * difference = current indicator value - previous indicator value
 * </pre>
 */
public class DifferenceIndicator extends NumericIndicator {
  private final NumericIndicator indicator;
  private Num previousValue;
  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator the bar indicator
   */
  public DifferenceIndicator(final NumericIndicator indicator) {
    super(indicator.getNumFactory());
    this.indicator = indicator;
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.previousValue != null;
  }


  /**
   * Calculates the difference between indicator values of the current bar and the
   * previous bar.
   *
   * @return the difference between the close prices
   */
  protected Num calculate() {
    return diff();
  }


  private Num diff() {
    if (this.previousValue == null) {
      this.previousValue = this.indicator.getValue();
      return this.previousValue;
    }

    final var indicatorValue = this.indicator.getValue();
    final var diff = indicatorValue.minus(this.previousValue);
    this.previousValue = indicatorValue;
    return diff;
  }
}

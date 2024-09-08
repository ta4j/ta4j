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

import org.ta4j.core.indicators.bool.BooleanIndicator;
import org.ta4j.core.indicators.helpers.previous.PreviousNumericValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * Cross indicator.
 *
 * <p>
 * Boolean indicator that monitors the crossing of two indicators.
 */
public class CrossIndicator extends BooleanIndicator {

  /** Upper indicator */
  private final NumericIndicator up;

  /** Lower indicator */
  private final NumericIndicator low;
  private final PreviousNumericValueIndicator previousUp;
  private final PreviousNumericValueIndicator previousLow;
  private Boolean value;
  private Instant currentTick = Instant.EPOCH;


  /**
   * @param up the upper indicator
   * @param low the lower indicator
   * @param barCount test whether crossed up within las barCount
   */
  public CrossIndicator(final NumericIndicator up, final NumericIndicator low, final int barCount) {
    this.up = up;
    this.low = low;
    this.previousUp = up.previous(barCount);
    this.previousLow = low.previous(barCount);
  }


  protected Boolean calculate() {
    final var greaterThanNow = this.up.getValue().isGreaterThan(this.low.getValue());
    final var lessThanPreviously = this.previousUp.getValue().isLessThanOrEqual(this.previousLow.getValue());
    return greaterThanNow && lessThanPreviously;
  }


  @Override
  public Boolean getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.low.refresh(tick);
      this.up.refresh(tick);
      this.previousUp.refresh(tick);
      this.previousLow.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.up.isStable() && this.low.isStable() && this.previousUp.isStable() && this.previousLow.isStable();
  }


  /** @return the initial lower indicator */
  public NumericIndicator getLow() {
    return this.low;
  }


  /** @return the initial upper indicator */
  public NumericIndicator getUp() {
    return this.up;
  }


  @Override
  public String toString() {
    return String.format(
        "Cross(%s, %s, %s, %s) => %s",
        this.up,
        this.low,
        this.previousUp,
        this.previousLow,
        getValue()
    );
  }
}

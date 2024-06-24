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

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.helpers.previous.PreviousNumericValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Loss indicator.
 *
 * <p>
 * Returns the difference of the indicator value of a bar and its previous bar
 * if the indicator value of the current bar is less than the indicator value of
 * the previous bar (otherwise, {@link NumFactory#zero()} is returned).
 */
public class LossIndicator extends NumericIndicator {

  private final NumericIndicator indicator;
  private final PreviousNumericValueIndicator previousValueIndicator;
  private Num value;
  private Instant currentTick = Instant.EPOCH;


  /**
   * Constructor.
   *
   * @param indicator the {@link Indicator}
   */
  public LossIndicator(final NumericIndicator indicator) {
    super(indicator.getNumFactory());
    this.indicator = indicator;
    this.previousValueIndicator = indicator.previous();
  }


  protected Num calculate() {
    if (!this.previousValueIndicator.isStable()) {
      return getNumFactory().zero();
    }
    final var actualValue = this.indicator.getValue();
    final var previousValue = this.previousValueIndicator.getValue();
    return actualValue.isLessThan(previousValue) ? previousValue.minus(actualValue)
                                                 : getNumFactory().zero();
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicator.refresh(tick);
      this.previousValueIndicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.indicator.isStable() && this.previousValueIndicator.isStable();
  }


  @Override
  public String toString() {
    return String.format("LOSS => %s", getValue());
  }
}

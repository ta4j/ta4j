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
package org.ta4j.core.indicators.statistics;

import java.time.Instant;

import org.ta4j.core.indicators.helpers.previous.PreviousNumericValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Variance indicator.
 */
public class VarianceIndicator extends NumericIndicator {

  private final NumericIndicator indicator;
  private final int barCount;
  private final Num divisor;
  private final PreviousNumericValueIndicator oldestValue;
  private Num mean;
  private int currentIndex;
  private Num value;
  private Instant currentTick = Instant.EPOCH;


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param barCount the time frame
   */
  public VarianceIndicator(final NumericIndicator indicator, final int barCount) {
    super(indicator.getNumFactory());
    if (barCount <= 1) {
      throw new IllegalArgumentException("barCount must be greater than 1");
    }

    this.barCount = barCount;
    this.indicator = indicator;
    this.divisor = getNumFactory().numOf(barCount - 1);
    this.mean = getNumFactory().zero();
    this.oldestValue = indicator.previous(barCount);
    this.value = getNumFactory().zero();
  }


  protected Num calculate() {
    if (this.currentIndex < this.barCount) {
      return add(this.indicator.getValue());
    }

    final var oldValue = this.oldestValue.getValue();
    return dropOldestAndAddNew(oldValue, this.indicator.getValue());
  }


  public Num add(final Num x) {
    this.currentIndex++;
    final var delta = x.minus(this.mean);
    this.mean = this.mean.plus(delta.dividedBy(getNumFactory().numOf(this.currentIndex)));
    return this.value.plus(delta.multipliedBy(x.minus(this.mean)));
  }


  private Num dropOldestAndAddNew(final Num x, final Num y) {
    final var deltaYX = y.minus(x);
    final var deltaX = x.minus(this.mean);
    final var deltaY = y.minus(this.mean);
    this.mean = this.mean.plus(deltaYX.dividedBy(getNumFactory().numOf(this.barCount)));
    final var deltaYp = y.minus(this.mean);
    return this.value.minus(
            getNumFactory().numOf(this.barCount)
                .multipliedBy(
                    deltaX.multipliedBy(deltaX)
                        .minus(deltaY.multipliedBy(deltaYp))
                        .dividedBy(this.divisor)
                )
        )
        .minus(deltaYX.multipliedBy(deltaYp).dividedBy(this.divisor))
        ;
  }


  @Override
  public Num getValue() {
    return this.value.dividedBy(this.divisor);
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicator.refresh(tick);
      this.oldestValue.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.currentIndex >= this.barCount && this.indicator.isStable();
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " barCount: " + this.barCount;
  }
}

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

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.average.SMAIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.CircularNumArray;

/**
 * Variance indicator.
 */
public class VarianceIndicator extends AbstractIndicator<Num> {

  private final int barCount;
  private final Num divisor;
  private final SMAIndicator mean;
  private final CircularNumArray values;
  private final Indicator<Num> indicator;
  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param barCount the time frame
   */
  public VarianceIndicator(final Indicator<Num> indicator, final int barCount) {
    super(indicator.getBarSeries());
    this.barCount = barCount;
    this.indicator = indicator;
    this.divisor = this.indicator.getBarSeries().numFactory().numOf(this.barCount - 1);
    this.mean = new SMAIndicator(indicator, barCount);

    if (barCount <= 1) {
      throw new IllegalArgumentException("barCount must be greater than 1");
    }

    this.values = new CircularNumArray(barCount);
  }


  protected Num calculate() {
    this.values.addLast(this.indicator.getValue());

    Num variance = getBarSeries().numFactory().zero();
    // cannot use RunningTotalIndicator because mean is changing each tick
    final Num average = this.mean.getValue();
    for (final var val : this.values) {
      final var diff = val.minus(average);
      final Num pow = diff.multipliedBy(diff);
      variance = variance.plus(pow);
    }

    variance = variance.dividedBy(this.divisor);
    return variance;
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.mean.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.indicator.isStable() && this.mean.isStable();
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " barCount: " + this.barCount;
  }
}

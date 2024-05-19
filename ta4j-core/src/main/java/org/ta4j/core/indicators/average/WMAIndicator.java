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
package org.ta4j.core.indicators.average;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.CircularNumArray;

/**
 * WMA indicator.
 */
public class WMAIndicator extends AbstractIndicator<Num> {

  private final int barCount;
  private final Indicator<Num> indicator;
  private final CircularNumArray values;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;
  private int barsPassed;


  /**
   * Constructor.
   *
   * @param indicator the {@link Indicator}
   * @param barCount the time frame
   */
  public WMAIndicator(final Indicator<Num> indicator, final int barCount) {
    super(indicator.getBarSeries());
    this.indicator = indicator;
    this.barCount = barCount;
    this.values = new CircularNumArray(barCount);
  }


  protected Num calculate() {
    if (this.values.isEmpty()) {
      final var indicatorValue = this.indicator.getValue();
      this.values.addLast(indicatorValue);
      return indicatorValue;
    }

    this.values.addLast(this.indicator.getValue());

    final var numFactory = getBarSeries().numFactory();
    Num wmaSum = numFactory.zero();
    int i = this.barCount;
    for (final var v : this.values.reversed()) {
      wmaSum = wmaSum.plus(v.multipliedBy(numFactory.numOf(i--)));
    }


    return wmaSum.dividedBy(numFactory.numOf((this.barCount * (this.barCount + 1)) / 2));
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.barsPassed;
      this.indicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.barsPassed >= this.barCount && this.indicator.isStable();
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " barCount: " + this.barCount;
  }
}

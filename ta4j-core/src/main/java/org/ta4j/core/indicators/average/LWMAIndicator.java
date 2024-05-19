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
import java.util.ArrayList;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.CircularNumArray;

/**
 * Linearly Weighted Moving Average (LWMA) indicator.
 *
 * @see <a href=
 *     "https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp">
 *     https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp</a>
 */
public class LWMAIndicator extends AbstractIndicator<Num> {

  private final Indicator<Num> indicator;
  private final int barCount;
  private final CircularNumArray values;
  private final ArrayList<Num> weights;
  private final Num denominator;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator the {@link Indicator}
   * @param barCount the time frame
   */
  public LWMAIndicator(final Indicator<Num> indicator, final int barCount) {
    super(indicator.getBarSeries());
    this.indicator = indicator;
    this.barCount = barCount;
    this.values = new CircularNumArray(barCount);
    this.weights = new ArrayList<>(barCount);
    final var numFactory = getBarSeries().numFactory();
    for (int i = 1; i < barCount + 1; i++) {
      this.weights.add(numFactory.numOf(i));
    }
    this.denominator = numFactory.numOf(this.weights.stream().mapToInt(Num::intValue).sum());
  }


  protected Num calculate() {
    final var numFactory = getBarSeries().numFactory();
    Num sum = numFactory.zero();

    this.values.addLast(this.indicator.getValue());

    if (this.values.isNotFull()) {
      return numFactory.zero();
    }

    int count = 0;
    for (final var val : this.values) {
      final var weight = this.weights.get(count++);
      sum = sum.plus(val.multipliedBy(weight));
    }

    return sum.dividedBy(this.denominator);
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " barCount: " + this.barCount;
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return false;
  }
}

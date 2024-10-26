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
package org.ta4j.core.indicators.numeric.statistics;

import static org.ta4j.core.num.NaN.NaN;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-Pearson-Correlation
 *
 * @see <a href=
 *     "http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/">
 *     http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/</a>
 */
public class PearsonCorrelationIndicator extends NumericIndicator {

  private final NumericIndicator indicator1;
  private final NumericIndicator indicator2;
  private final int barCount;
  private final Deque<XY> window;
  private Num sx;
  private Num sy;
  private Num sxx;
  private Num syy;
  private Num sxy;
  private Instant currentTick = Instant.EPOCH;
  private Num value;
  private final Num n;


  /**
   * Constructor.
   *
   * @param indicator1 the first indicator
   * @param indicator2 the second indicator
   * @param barCount the time frame
   */
  public PearsonCorrelationIndicator(
      final NumericIndicator indicator1,
      final NumericIndicator indicator2,
      final int barCount
  ) {
    super(indicator1.getNumFactory());
    this.indicator1 = indicator1;
    this.indicator2 = indicator2;
    this.barCount = barCount;
    this.window = new ArrayDeque<>(barCount);
    this.sx = getNumFactory().zero();
    this.sy = getNumFactory().zero();
    this.sxx = getNumFactory().zero();
    this.sxy = getNumFactory().zero();
    this.syy = getNumFactory().zero();
    this.n = getNumFactory().numOf(this.barCount);
  }


  protected Num calculate() {

    final var x = this.indicator1.getValue();
    final var y = this.indicator2.getValue();
    this.window.offer(new XY(x, y));

    if (this.window.size() > this.barCount) {
      final var polled = this.window.poll();
      removeOldValue(polled);
    }


    this.sx = this.sx.plus(x);
    this.sy = this.sy.plus(y);
    this.sxy = this.sxy.plus(x.multipliedBy(y));
    this.sxx = this.sxx.plus(x.multipliedBy(x));
    this.syy = this.syy.plus(y.multipliedBy(y));

    // (n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy)
    final var toSqrt = (this.n.multipliedBy(this.sxx).minus(this.sx.multipliedBy(this.sx)))
        .multipliedBy(this.n.multipliedBy(this.syy).minus(this.sy.multipliedBy(this.sy)));

    if (toSqrt.isGreaterThan(getNumFactory().zero())) {
      // pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy *
      // Sy))
      return (this.n.multipliedBy(this.sxy).minus(this.sx.multipliedBy(this.sy))).dividedBy(toSqrt.sqrt());
    }

    return NaN;
  }


  private void removeOldValue(final XY polled) {
    this.sx = this.sx.minus(polled.x());
    this.sy = this.sy.minus(polled.y());
    this.sxy = this.sxy.minus(polled.x().multipliedBy(polled.y()));
    this.sxx = this.sxx.minus(polled.x().multipliedBy(polled.x()));
    this.syy = this.syy.minus(polled.y().multipliedBy(polled.y()));
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicator1.refresh(tick);
      this.indicator2.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.indicator1.isStable() && this.indicator2.isStable();
  }


  private record XY(Num x, Num y) {
  }
}

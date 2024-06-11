/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.helpers.previous.PreviousNumericValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Zero-lag exponential moving average indicator.
 *
 * @see <a href=
 *     "http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm">
 *     http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm</a>
 */
public class ZLEMAIndicator extends NumericIndicator {

  private final NumericIndicator indicator;
  private final int barCount;
  private final Num k;
  private final Num oneMinusK;
  private final PreviousNumericValueIndicator lagPreviousValue;
  private final int lag;
  private int barsPassed;
  private Num value;
  private Instant currentTick = Instant.EPOCH;


  /**
   * Constructor.
   *
   * @param indicator the {@link Indicator}
   * @param barCount the time frame
   */
  public ZLEMAIndicator(final NumericIndicator indicator, final int barCount) {
    super(indicator.getNumFactory());
    this.indicator = indicator;
    this.barCount = barCount;
    this.k = getNumFactory().two().dividedBy(getNumFactory().numOf(barCount + 1));
    this.oneMinusK = getNumFactory().one().minus(this.k);
    this.lag = (barCount - 1) / 2;

    if (this.lag == 0) {
      throw new IllegalArgumentException("The bar count must be greater than 2");
    }

    this.lagPreviousValue = indicator.previous(this.lag);
  }


  protected Num calculate() {
    if (this.barsPassed <= this.lag) {
      return this.indicator.getValue();
    }

    final Num zlemaPrev = getValue();
    return this.k.multipliedBy(
            getNumFactory()
                .two()
                .multipliedBy(this.indicator.getValue())
                .minus(this.lagPreviousValue.getValue())
        )
        .plus(this.oneMinusK.multipliedBy(zlemaPrev));
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
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.barsPassed;
      this.indicator.refresh(tick);
      this.lagPreviousValue.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.barsPassed >= this.lag && this.lagPreviousValue.isStable() && this.indicator.isStable();
  }
}

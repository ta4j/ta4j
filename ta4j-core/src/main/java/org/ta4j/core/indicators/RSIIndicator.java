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
package org.ta4j.core.indicators;

import java.time.Instant;

import org.ta4j.core.indicators.average.MMAIndicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Relative strength index indicator.
 *
 * <p>
 * Computed using original Welles Wilder formula.
 */
public class RSIIndicator extends NumericIndicator {

  private final MMAIndicator averageGainIndicator;
  private final MMAIndicator averageLossIndicator;
  private Num value;
  private Instant currentTick = Instant.EPOCH;


  /**
   * Constructor.
   *
   * @param indicator the {@link Indicator}
   * @param barCount the time frame
   */
  public RSIIndicator(final NumericIndicator indicator, final int barCount) {
    super(indicator.getNumFactory());
    this.averageGainIndicator = new MMAIndicator(new GainIndicator(indicator), barCount);
    this.averageLossIndicator = new MMAIndicator(new LossIndicator(indicator), barCount);
    this.value = getNumFactory().zero();
  }


  protected Num calculate() {
    // compute relative strength
    final var averageGain = this.averageGainIndicator.getValue();
    final var averageLoss = this.averageLossIndicator.getValue();
    final var numFactory = getNumFactory();
    if (averageLoss.isZero()) {
      return averageGain.isZero() ? numFactory.zero() : numFactory.hundred();
    }

    final var relativeStrength = averageGain.dividedBy(averageLoss);
    // compute relative strength index
    return numFactory.hundred().minus(numFactory.hundred().dividedBy(numFactory.one().plus(relativeStrength)));
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.averageGainIndicator.refresh(tick);
      this.averageLossIndicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.averageGainIndicator.isStable() && this.averageLossIndicator.isStable();
  }
}

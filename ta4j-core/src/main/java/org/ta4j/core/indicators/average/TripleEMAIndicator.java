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

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Triple exponential moving average indicator (also called "TRIX").
 *
 * <p>
 * TEMA needs "3 * period - 2" of data to start producing values in contrast to
 * the period samples needed by a regular EMA.
 *
 * @see <a href=
 *     "https://en.wikipedia.org/wiki/Triple_exponential_moving_average">https://en.wikipedia.org/wiki/Triple_exponential_moving_average</a>
 * @see <a href=
 *     "https://www.investopedia.com/terms/t/triple-exponential-moving-average.asp">https://www.investopedia.com/terms/t/triple-exponential-moving-average.asp</a>
 */
public class TripleEMAIndicator extends NumericIndicator {

  private final int barCount;
  private final EMAIndicator ema;
  private final EMAIndicator emaEma;
  private final EMAIndicator emaEmaEma;
  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param barCount the time frame
   */
  public TripleEMAIndicator(final NumericIndicator indicator, final int barCount) {
    super(indicator.getNumFactory());
    this.barCount = barCount;
    this.ema = new EMAIndicator(indicator, barCount);
    this.emaEma = new EMAIndicator(this.ema, barCount);
    this.emaEmaEma = new EMAIndicator(this.emaEma, barCount);
  }


  protected Num calculate() {
    // trix = 3 * ( ema - emaEma ) + emaEmaEma
    final var numFactory = getNumFactory();
    return numFactory.numOf(3)
        .multipliedBy(this.ema.getValue().minus(this.emaEma.getValue()))
        .plus(this.emaEmaEma.getValue());
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
      this.ema.refresh(tick);
      this.emaEma.refresh(tick);
      this.emaEmaEma.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.ema.isStable() && this.emaEma.isStable() && this.emaEmaEma.isStable();
  }
}

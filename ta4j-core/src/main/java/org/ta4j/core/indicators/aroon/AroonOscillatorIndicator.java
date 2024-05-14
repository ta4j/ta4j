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
package org.ta4j.core.indicators.aroon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Aroon Oscillator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator</a>
 */
public class AroonOscillatorIndicator extends AbstractIndicator<Num> {

  private final int barCount;
  private final AroonUpIndicator aroonUpIndicator;
  private final AroonDownIndicator aroonDownIndicator;
  private final ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;


  /**
   * Constructor.
   *
   * @param series the bar series
   * @param barCount the number of periods used for the indicators
   */
  public AroonOscillatorIndicator(final BarSeries series, final int barCount) {
    super(series);
    this.barCount = barCount;
    this.aroonUpIndicator = new AroonUpIndicator(series, barCount);
    this.aroonDownIndicator = new AroonDownIndicator(series, barCount);
  }


  protected Num calculate() {
    return this.aroonUpIndicator.getValue().minus(this.aroonDownIndicator.getValue());
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " barCount: " + this.barCount;
  }


  /** @return the {@link #aroonUpIndicator} */
  public AroonUpIndicator getAroonUpIndicator() {
    return this.aroonUpIndicator;
  }


  /** @return the {@link #aroonDownIndicator} */
  public AroonDownIndicator getAroonDownIndicator() {
    return this.aroonDownIndicator;
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      this.aroonUpIndicator.refresh(tick);
      this.aroonDownIndicator.refresh(tick);
      this.value = calculate();
    }
  }


  @Override
  public boolean isStable() {
    return this.aroonUpIndicator.isStable() && this.aroonDownIndicator.isStable();
  }
}

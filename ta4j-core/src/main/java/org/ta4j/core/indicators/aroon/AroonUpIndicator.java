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

import static org.ta4j.core.num.NaN.NaN;

import java.time.Instant;
import java.util.ArrayList;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.candles.price.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Aroon up indicator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon">chart_school:technical_indicators:aroon</a>
 */
public class AroonUpIndicator extends NumericIndicator {

  private final int barCount;
  private final HighestValueIndicator highestHighValueIndicator;
  private final Indicator<Num> highIndicator;

  private Instant currentTick = Instant.EPOCH;
  private int index;
  private final ArrayList<Num> previousValues;
  private Num value;


  /**
   * Constructor.
   *
   * @param highIndicator the indicator for the high price (default
   *     {@link HighPriceIndicator})
   * @param barCount the time frame
   */
  public AroonUpIndicator(final BarSeries series, final NumericIndicator highIndicator, final int barCount) {
    super(series.numFactory());
    this.barCount = barCount;
    this.highIndicator = highIndicator;
    this.previousValues = new ArrayList<>(barCount);
    for (int i = 0; i < barCount; i++) {
      this.previousValues.add(NaN);
    }
    // + 1 needed for last possible iteration in loop
    this.highestHighValueIndicator = new HighestValueIndicator(highIndicator, barCount + 1);
  }


  /**
   * Default Constructor with {@code highPriceIndicator} =
   * {@link HighPriceIndicator}.
   *
   * @param series the bar series
   * @param barCount the time frame
   */
  public AroonUpIndicator(final BarSeries series, final int barCount) {
    this(series, new HighPriceIndicator(series), barCount);
  }


  protected Num calculate() {
    final var currentLow = this.highIndicator.getValue();
    this.previousValues.set(getIndex(this.index), currentLow);

    if (currentLow.isNaN()) {
      return NaN;
    }

    final var lowestValue = this.highestHighValueIndicator.getValue();

    final var barCountFromLastMaximum = countBarsBetweenHighs(lowestValue);
    return getNumFactory().numOf((double) (this.barCount - barCountFromLastMaximum) / this.barCount * 100.0);
  }


  private int countBarsBetweenHighs(final Num lowestValue) {
    for (int i = getIndex(this.index), barDistance = 0; barDistance < this.barCount; barDistance++, i--) {
      if (this.previousValues.get(getIndex(this.barCount + i)).equals(lowestValue)) {
        return barDistance;
      }
    }
    return this.barCount;
  }


  private int getIndex(final int i) {
    return i % this.barCount;
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.index;
      this.highIndicator.refresh(tick);
      this.highestHighValueIndicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.index > this.barCount && this.highIndicator.isStable() && this.highestHighValueIndicator.isStable();
  }


  @Override
  public String toString() {
    return String.format("AroonUp(%s) => %s", this.highIndicator, getValue());
  }

}

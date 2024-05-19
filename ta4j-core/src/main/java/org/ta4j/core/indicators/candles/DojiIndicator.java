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
package org.ta4j.core.indicators.candles;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.average.SMAIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * Doji indicator.
 *
 * <p>
 * A candle/bar is considered Doji if its body height is lower than the average
 * multiplied by a factor.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji">
 *     http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji</a>
 */
public class DojiIndicator extends AbstractIndicator<Boolean> {

  /** Body height. */
  private final Indicator<Num> bodyHeightInd;

  /** Average body height. */
  private final PreviousValueIndicator averageBodyHeightInd;

  /** The factor used when checking if a candle is Doji. */
  private final Num factor;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Boolean value;


  /**
   * Constructor.
   *
   * @param series the bar series
   * @param barCount the number of bars used to calculate the average body
   *     height
   * @param bodyFactor the factor used when checking if a candle is Doji
   */
  public DojiIndicator(final BarSeries series, final int barCount, final double bodyFactor) {
    super(series);
    this.bodyHeightInd = TransformIndicator.abs(new RealBodyIndicator(series));
    this.averageBodyHeightInd = new PreviousValueIndicator(new SMAIndicator(this.bodyHeightInd, barCount), 1);
    this.factor = getBarSeries().numFactory().numOf(bodyFactor);
  }


  protected Boolean calculate() {
    if (this.value == null) {
      return this.bodyHeightInd.getValue().isZero();
    }

    final Num averageBodyHeight = this.averageBodyHeightInd.getValue();
    final Num currentBodyHeight = this.bodyHeightInd.getValue();

    return currentBodyHeight.isLessThan(averageBodyHeight.multipliedBy(this.factor));
  }


  @Override
  public Boolean getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      this.bodyHeightInd.refresh(tick);
      this.averageBodyHeightInd.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.value != null;
  }
}

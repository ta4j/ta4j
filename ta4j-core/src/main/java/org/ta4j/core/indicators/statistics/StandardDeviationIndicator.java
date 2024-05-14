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
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;

/**
 * Standard deviation indicator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:standard_deviation_volatility</a>
 */
public class StandardDeviationIndicator extends AbstractIndicator<Num> {

  private final VarianceIndicator variance;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param barCount the time frame
   */
  public StandardDeviationIndicator(final Indicator<Num> indicator, final int barCount) {
    super(indicator.getBarSeries());
    this.variance = new VarianceIndicator(indicator, barCount);
  }


  protected Num calculate() {
    return this.variance.getValue().sqrt();
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      this.variance.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.variance.isStable();
  }
}

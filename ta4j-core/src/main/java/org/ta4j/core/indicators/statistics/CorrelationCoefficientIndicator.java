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

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Correlation coefficient indicator.
 *
 * @see <a href=
 *     "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/correlation-coefficient">
 *     https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/correlation-coefficient</a>
 */
public class CorrelationCoefficientIndicator extends NumericIndicator {

  private final VarianceIndicator variance1;
  private final VarianceIndicator variance2;
  private final CovarianceIndicator covariance;
  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator1 the first indicator
   * @param indicator2 the second indicator
   * @param barCount the time frame
   */
  public CorrelationCoefficientIndicator(
      final NumericIndicator indicator1,
      final NumericIndicator indicator2,
      final int barCount
  ) {
    super(indicator1.getNumFactory());
    this.variance1 = new VarianceIndicator(indicator1, barCount);
    this.variance2 = new VarianceIndicator(indicator2, barCount);
    this.covariance = new CovarianceIndicator(indicator1, indicator2, barCount);
  }


  protected Num calculate() {
    final var cov = this.covariance.getValue();
    final var var1 = this.variance1.getValue();
    final var var2 = this.variance2.getValue();
    final var multipliedSqrt = var1.multipliedBy(var2).sqrt();
    return cov.dividedBy(multipliedSqrt);
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.variance1.refresh(tick);
      this.variance2.refresh(tick);
      this.covariance.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.variance1.isStable() && this.variance2.isStable() && this.covariance.isStable();
  }
}

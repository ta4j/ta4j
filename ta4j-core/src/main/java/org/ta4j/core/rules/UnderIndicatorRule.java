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
package org.ta4j.core.rules;

import java.time.Instant;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.helpers.ConstantNumericIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * A rule that monitors when one {@link Indicator indicator} is below another.
 *
 * <p>
 * Satisfied when the value of the first {@link Indicator indicator} is strictly
 * less than the value of the second one.
 */
public class UnderIndicatorRule extends AbstractRule {

  /** The first indicator. */
  private final NumericIndicator first;

  /** The second indicator. */
  private final NumericIndicator second;


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param threshold the threshold
   */
  public UnderIndicatorRule(final NumericIndicator indicator, final Number threshold) {
    this(indicator, new ConstantNumericIndicator(indicator.getNumFactory().numOf(threshold)));
  }


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param threshold the threshold
   */
  public UnderIndicatorRule(final NumericIndicator indicator, final Num threshold) {
    this(indicator, new ConstantNumericIndicator(threshold));
  }


  /**
   * Constructor.
   *
   * @param first the first indicator
   * @param second the second indicator
   */
  public UnderIndicatorRule(final NumericIndicator first, final NumericIndicator second) {
    this.first = first;
    this.second = second;
  }


  /** This rule does not use the {@code tradingRecord}. */
  @Override
  public boolean isSatisfied(final TradingRecord tradingRecord) {
    final boolean satisfied = this.first.getValue().isLessThan(this.second.getValue());
    traceIsSatisfied(satisfied);
    return satisfied;
  }


  @Override
  public void refresh(final Instant tick) {
    this.first.refresh(tick);
    this.second.refresh(tick);
    setCurrentTick(tick);
  }


  @Override
  public boolean isStable() {
    return this.first.isStable() && this.second.isStable();
  }
}

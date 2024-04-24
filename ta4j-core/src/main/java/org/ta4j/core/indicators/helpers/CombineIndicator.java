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
package org.ta4j.core.indicators.helpers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;

/**
 * Combine indicator.
 *
 * <p>
 * Combines two Num indicators by using common math operations.
 */
public class CombineIndicator extends AbstractIndicator<Num> {

  private final Indicator<Num> indicatorLeft;
  private final Indicator<Num> indicatorRight;
  private final BinaryOperator<Num> combineFunction;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;


  /**
   * Constructor.
   *
   * @param indicatorLeft the indicator for the left hand side of the calculation
   * @param indicatorRight the indicator for the right hand side of the
   *     calculation
   * @param combination a {@link Function} describing the combination function
   *     to combine the values of the indicators
   */
  public CombineIndicator(
      final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight,
      final BinaryOperator<Num> combination
  ) {
    // TODO check both indicators use the same series/num function
    super(indicatorLeft.getBarSeries());
    this.indicatorLeft = indicatorLeft;
    this.indicatorRight = indicatorRight;
    this.combineFunction = combination;
  }


  protected Num calculate() {
    return this.combineFunction.apply(this.indicatorLeft.getValue(), this.indicatorRight.getValue());
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicatorLeft.refresh(tick);
      this.indicatorRight.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.indicatorLeft.isStable() && this.indicatorRight.isStable();
  }


  /**
   * Combines the two input indicators by indicatorLeft.plus(indicatorRight).
   */
  public static CombineIndicator plus(final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight) {
    return new CombineIndicator(indicatorLeft, indicatorRight, Num::plus);
  }


  /**
   * Combines the two input indicators by indicatorLeft.minus(indicatorRight).
   */
  public static CombineIndicator minus(final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight) {
    return new CombineIndicator(indicatorLeft, indicatorRight, Num::minus);
  }


  /**
   * Combines the two input indicators by indicatorLeft.dividedBy(indicatorRight).
   */
  public static CombineIndicator divide(final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight) {
    return new CombineIndicator(indicatorLeft, indicatorRight, Num::dividedBy);
  }


  /**
   * Combines the two input indicators by
   * indicatorLeft.multipliedBy(indicatorRight).
   */
  public static CombineIndicator multiply(final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight) {
    return new CombineIndicator(indicatorLeft, indicatorRight, Num::multipliedBy);
  }


  /**
   * Combines the two input indicators by indicatorLeft.max(indicatorRight).
   */
  public static CombineIndicator max(final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight) {
    return new CombineIndicator(indicatorLeft, indicatorRight, Num::max);
  }


  /**
   * Combines the two input indicators by indicatorLeft.min(indicatorRight).
   */
  public static CombineIndicator min(final Indicator<Num> indicatorLeft, final Indicator<Num> indicatorRight) {
    return new CombineIndicator(indicatorLeft, indicatorRight, Num::min);
  }
}

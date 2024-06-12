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
package org.ta4j.core.indicators.numeric;

import java.time.Instant;
import java.util.function.UnaryOperator;

import org.ta4j.core.num.Num;

/**
 * Objects of this class defer the evaluation of a unary operator, like sqrt().
 *
 * There may be other unary operations on Num that could be added here.
 */
public class UnaryOperation extends NumericIndicator {

  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Returns an {@code Indicator} whose value is {@code √(operand)}.
   *
   * @param operand
   *
   * @return {@code √(operand)}
   *
   * @see Num#sqrt
   */
  public static UnaryOperation sqrt(final NumericIndicator operand) {
    return new UnaryOperation(Num::sqrt, operand);
  }


  /**
   * Returns an {@code Indicator} whose value is the absolute value of
   * {@code operand}.
   *
   * @param operand
   *
   * @return {@code abs(operand)}
   *
   * @see Num#abs
   */
  public static UnaryOperation abs(final NumericIndicator operand) {
    return new UnaryOperation(Num::abs, operand);
  }


  private final UnaryOperator<Num> operator;
  private final NumericIndicator operand;


  private UnaryOperation(final UnaryOperator<Num> operator, final NumericIndicator operand) {
    super(operand.getNumFactory());
    this.operator = operator;
    this.operand = operand;
  }


  private Num calculate() {
    final var n = this.operand.getValue();
    return this.operator.apply(n);
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.operand.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.operand.isStable();
  }


  @Override
  public String toString() {
    return String.format("UI<%s> => %s", this.operand, getValue());
  }
}

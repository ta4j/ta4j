/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Lukáš Kvídera
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
package org.ta4j.core.indicators.bool;

import static org.ta4j.core.num.NaN.NaN;

import java.time.Instant;

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.previous.PreviousNumericValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * A rule that monitors when an {@link Indicator} shows a specified slope.
 *
 * <p>
 * Satisfied when the difference of the value of the {@link Indicator indicator}
 * and its previous (n-th) value is between the values of {@code maxSlope}
 * or/and {@code minSlope}. It can test both, positive and negative slope.
 */
public class InSlopeIndicator extends BooleanIndicator {

  /** The minimum slope between ref and prev. */
  private final Num minSlope;

  /** The maximum slope between ref and prev. */
  private final Num maxSlope;
  private final CombineIndicator diff;
  private Boolean value;
  private Instant currentTick = Instant.EPOCH;


  /**
   * Constructor.
   *
   * @param ref the reference indicator
   * @param minSlope minumum slope between reference and previous indicator
   */
  public InSlopeIndicator(final NumericIndicator ref, final Num minSlope) {
    this(ref, 1, minSlope, NaN);
  }


  /**
   * Constructor.
   *
   * @param ref the reference indicator
   * @param minSlope minumum slope between value of reference and previous
   *     indicator
   * @param maxSlope maximum slope between value of reference and previous
   *     indicator
   */
  public InSlopeIndicator(final NumericIndicator ref, final Num minSlope, final Num maxSlope) {
    this(ref, 1, minSlope, maxSlope);
  }


  /**
   * Constructor.
   *
   * @param ref the reference indicator
   * @param nthPrevious defines the previous n-th indicator
   * @param maxSlope maximum slope between value of reference and previous
   *     indicator
   */
  public InSlopeIndicator(final NumericIndicator ref, final int nthPrevious, final Num maxSlope) {
    this(ref, nthPrevious, NaN, maxSlope);
  }


  /**
   * Constructor.
   *
   * @param ref the reference indicator
   * @param nthPrevious defines the previous n-th indicator
   * @param minSlope minumum slope between value of reference and previous
   *     indicator
   * @param maxSlope maximum slope between value of reference and previous
   *     indicator
   */
  public InSlopeIndicator(final NumericIndicator ref, final int nthPrevious, final Num minSlope, final Num maxSlope) {
    this.diff = CombineIndicator.minus(ref, new PreviousNumericValueIndicator(ref, nthPrevious));
    this.minSlope = minSlope;
    this.maxSlope = maxSlope;
  }


  public boolean calculate() {
    final var val = this.diff.getValue();
    final var minSlopeSatisfied = this.minSlope.isNaN() || val.isGreaterThanOrEqual(this.minSlope);
    final var maxSlopeSatisfied = this.maxSlope.isNaN() || val.isLessThanOrEqual(this.maxSlope);
    final var isNaN = this.minSlope.isNaN() && this.maxSlope.isNaN();

    return minSlopeSatisfied && maxSlopeSatisfied && !isNaN;
  }


  @Override
  public Boolean getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.diff.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.diff.isStable();
  }
}

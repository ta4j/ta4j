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
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

/**
 * Transform indicator.
 *
 * <p>
 * Transforms the {@link Num} of any indicator by using common math operations.
 */
public class TransformIndicator extends AbstractIndicator<Num> {

  private final Indicator<Num> indicator;
  private final UnaryOperator<Num> transformationFunction;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;


  /**
   * Constructor.
   *
   * @param indicator the {@link Indicator}
   * @param transformation a {@link Function} describing the transformation
   */
  public TransformIndicator(final Indicator<Num> indicator, final UnaryOperator<Num> transformation) {
    super(indicator.getBarSeries());
    this.indicator = indicator;
    this.transformationFunction = transformation;
  }


  protected Num calculate() {
    return this.transformationFunction.apply(this.indicator.getValue());
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      this.indicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.indicator.isStable();
  }


  /**
   * Transforms the input indicator by indicator.plus(coefficient).
   */
  public static TransformIndicator plus(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.plus(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.minus(coefficient).
   */
  public static TransformIndicator minus(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.minus(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.dividedBy(coefficient).
   */
  public static TransformIndicator divide(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.dividedBy(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.multipliedBy(coefficient).
   */
  public static TransformIndicator multiply(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.multipliedBy(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.max(coefficient).
   */
  public static TransformIndicator max(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.max(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.min(coefficient).
   */
  public static TransformIndicator min(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.min(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.abs().
   */
  public static TransformIndicator abs(final Indicator<Num> indicator) {
    return new TransformIndicator(indicator, Num::abs);
  }


  /**
   * Transforms the input indicator by indicator.pow(coefficient).
   */
  public static TransformIndicator pow(final Indicator<Num> indicator, final Number coefficient) {
    final Num numCoefficient = indicator.getBarSeries().numFactory().numOf(coefficient);
    return new TransformIndicator(indicator, val -> val.pow(numCoefficient));
  }


  /**
   * Transforms the input indicator by indicator.sqrt().
   */
  public static TransformIndicator sqrt(final Indicator<Num> indicator) {
    return new TransformIndicator(indicator, Num::sqrt);
  }


  /**
   * Transforms the input indicator by indicator.log().
   */
  public static TransformIndicator log(final Indicator<Num> indicator) {
    return new TransformIndicator(
        indicator,
        val -> DecimalNumFactory.getInstance().numOf(Math.log(val.doubleValue()))
    );
  }


  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}

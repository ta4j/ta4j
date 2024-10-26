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
package org.ta4j.core.indicators.numeric.statistics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Simple linear regression indicator.
 *
 * <p>
 * A moving (i.e. over the time frame) simple linear regression (least squares).
 *
 * <pre>
 * y = slope * x + intercept
 * </pre>
 *
 * see <a href=
 * "https://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html">LinearRegression</a>
 */
public class SimpleLinearRegressionIndicator extends NumericIndicator {


  private final NumericIndicator indicator;
  private final int barCount;
  private final Deque<XY> window;
  private Num sumX;
  private Num sumY;
  private Num sumXY;
  private Num sumXX;
  private Instant currentTick = Instant.EPOCH;
  private final SimpleLinearRegressionType type;
  private int barsPassed;
  private Num value;


  /**
   * Constructor for the y-values of the formula (y = slope * x + intercept).
   *
   * @param indicator the indicator for the x-values of the formula.
   * @param barCount the time frame
   */
  public SimpleLinearRegressionIndicator(final NumericIndicator indicator, final int barCount) {
    this(indicator, barCount, SimpleLinearRegressionType.Y);
  }


  /**
   * Constructor.
   *
   * @param indicator the indicator for the x-values of the formula.
   * @param barCount the time frame
   * @param type the type of the outcome value (y, slope, intercept)
   */
  public SimpleLinearRegressionIndicator(
      final NumericIndicator indicator,
      final int barCount,
      final SimpleLinearRegressionType type
  ) {
    super(indicator.getNumFactory());
    this.indicator = indicator;
    this.barCount = barCount;
    this.type = type;
    this.window = new ArrayDeque<>(barCount);
    this.sumX = getNumFactory().zero();
    this.sumY = getNumFactory().zero();
    this.sumXX = getNumFactory().zero();
    this.sumXY = getNumFactory().zero();
  }


  protected Num calculate() {
    calculateRegressionLine();

    if (this.type == SimpleLinearRegressionType.SLOPE) {
      return getSlope();
    }

    if (this.type == SimpleLinearRegressionType.INTERCEPT) {
      return getIntercept();
    }

    return getSlope().multipliedBy(getNumFactory().numOf(this.barCount)).plus(getIntercept());
  }


  /**
   * Calculates the regression line.
   */
  private void calculateRegressionLine() {
    if (this.window.size() == this.barCount) {
      final var old = this.window.remove();
      this.sumX = this.sumX.minus(old.x());
      this.sumY = this.sumY.minus(old.y());
      this.sumXX = this.sumXX.minus(old.x().multipliedBy(old.x()));
      this.sumXY = this.sumXY.minus(old.x().multipliedBy(old.y()));
    }

    final var x = getNumFactory().numOf(this.barsPassed);
    final var y = this.indicator.getValue();
    this.window.offer(new XY(x, y));

    this.sumX = this.sumX.plus(x);
    this.sumY = this.sumY.plus(y);
    this.sumXX = this.sumXX.plus(x.multipliedBy(x));
    this.sumXY = this.sumXY.plus(x.multipliedBy(y));
  }


  private Num getSlope() {
    final int n = this.window.size();
    if (n < 2) {
      return getNumFactory().zero(); // Not enough points for regression
    }

    final var nNum = getNumFactory().numOf(n);
    final var numerator = nNum.multipliedBy(this.sumXY).minus(this.sumX.multipliedBy(this.sumY));
    final var denominator = nNum.multipliedBy(this.sumXX).minus(this.sumX.multipliedBy(this.sumX));
    return numerator.dividedBy(denominator);
  }


  private Num getIntercept() {
    final int n = this.window.size();
    if (n < 2) {
      return getNumFactory().zero(); // Not enough points for regression
    }

    final var nNum = getNumFactory().numOf(n);
    final var xMean = this.sumX.dividedBy(nNum);
    final var yMean = this.sumY.dividedBy(nNum);

    return yMean.minus(getSlope().multipliedBy(xMean));
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.barsPassed;
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
   * The type for the outcome of the {@link SimpleLinearRegressionIndicator}.
   */
  public enum SimpleLinearRegressionType {
    Y,
    SLOPE,
    INTERCEPT
  }

  private record XY(Num x, Num y) {
  }
}

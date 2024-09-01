/**
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
package org.ta4j.core.indicators.statistics;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Mean deviation indicator.
 *
 * @see <a href=
 *     "http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation">
 *     http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation</a>
 */
public class MeanDeviationIndicator extends NumericIndicator {

  private final NumericIndicator indicator;
  private final int barCount;
  private final Queue<Num> window = new LinkedList<>();
  private final Num numBarCount;
  private Instant currentTick = Instant.EPOCH;
  private int currentBar;
  private Num sum;
  private Num deviationSum;
  private Num value;
  private final Num divisor;


  /**
   * Constructor.
   *
   * @param indicator the indicator
   * @param barCount the time frame
   */
  public MeanDeviationIndicator(final NumericIndicator indicator, final int barCount) {
    super(indicator.getNumFactory());
    this.indicator = indicator;
    this.barCount = barCount;
    this.sum = getNumFactory().zero();
    this.deviationSum = getNumFactory().zero();
    this.numBarCount = getNumFactory().numOf(barCount);
    this.divisor = getNumFactory().numOf(this.barCount);
  }


  protected Num calculate() {
    if (this.window.size() == this.barCount) {
      stablePath();
    }

    return unstablePath();
  }


  private Num unstablePath() {
    // Add new value
    final var newValue = this.indicator.getValue();
    this.window.offer(newValue);
    final Num oldMean =
        this.window.size() == 1 ? newValue : this.sum.dividedBy(getNumFactory().numOf(this.window.size() - 1));
    this.sum = this.sum.plus(newValue);
    final Num newMean = this.sum.dividedBy(getNumFactory().numOf(this.window.size()));

    // Update deviationSum
    this.deviationSum = this.deviationSum.plus(newValue.minus(newMean).abs());

    // Adjust other values in the window for the new mean
    for (final var value : this.window) {
      if (!value.equals(newValue)) {
        this.deviationSum = this.deviationSum.plus(
            value.minus(newMean).abs().minus(value.minus(oldMean).abs())
        );
      }
    }

    // Calculate and return the mean deviation
    return this.deviationSum.dividedBy(getNumFactory().numOf(this.window.size()));
  }


  private void stablePath() {
    final Num oldestValue = this.window.poll();
    final Num oldMean = this.sum.dividedBy(this.divisor);

    // Remove contribution of oldest value
    this.sum = this.sum.minus(oldestValue);
    this.deviationSum = this.deviationSum.minus(oldestValue.minus(oldMean).abs());

    // Adjust deviationSum for the change in mean
    for (final var value : this.window) {
      this.deviationSum = this.deviationSum.plus(
          value.minus(this.sum.dividedBy(getNumFactory().numOf(this.barCount - 1))).abs()
              .minus(value.minus(oldMean).abs())
      );
    }
  }


  @Override
  public String toString() {
    return String.format("MDI(%d) => %s", this.numBarCount, getValue());
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.currentBar;
      this.indicator.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.currentBar >= this.barCount;
  }
}

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
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.indicators.Indicator;

/**
 * A fixed indicator.
 *
 * <p>
 * Returns constant values for a bar.
 *
 * @param <T> the type of returned constant values (Double, Boolean, etc.)
 */
class FixedIndicator<T> implements Indicator<T> {

  private final List<T> values = new ArrayList<>();
  private Instant currentTick = Instant.EPOCH;
  private int index = -1;


  /**
   * Constructor.
   *
   * @param values the values to be returned by this indicator
   */
  @SafeVarargs
  protected FixedIndicator(final T... values) {
    this.values.addAll(List.of(values));
  }


  /**
   * Adds the {@code value} to {@link #values}.
   *
   * @param value the value to add
   */
  public void addValue(final T value) {
    this.values.add(value);
  }


  @Override
  public T getValue() {
    return this.values.get(this.index);
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.index;
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return true;
  }
}

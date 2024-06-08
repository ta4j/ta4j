
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

package org.ta4j.core.indicators;

import java.time.Instant;

/**
 * @author Lukáš Kvídera
 */
public class NamedIndicator<T> implements Indicator<T> {

  private final String name;
  private final Indicator<T> indicator;


  private NamedIndicator(final String name, final Indicator<T> indicator) {
    this.name = name;
    this.indicator = indicator;
  }


  public static <T> NamedIndicator<T> of(final String name, final Indicator<T> indicator) {
    return new NamedIndicator<>(name, indicator);
  }


  @Override
  public T getValue() {
    return this.indicator.getValue();
  }


  @Override
  public void refresh(final Instant tick) {
    this.indicator.refresh(tick);
  }


  @Override
  public boolean isStable() {
    return this.indicator.isStable();
  }


  public String getName() {
    return this.name;
  }


  @Override
  public String toString() {
    return this.indicator.toString();
  }
}


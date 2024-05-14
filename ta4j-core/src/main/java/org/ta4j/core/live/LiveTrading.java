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
package org.ta4j.core.live;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

/**
 * Trading contest that traces bars and respective strategy
 *
 * @author Lukáš Kvídera
 */
public class LiveTrading {
  private final BarSeries series;
  private Strategy strategy;

  public LiveTrading(final BarSeries series, final Strategy strategy) {
    this.series = series;
    this.strategy = strategy;
  }

  public BarBuilder barBuilder() {
    return this.series.barBuilder();
  }

  public Strategy getStrategy() {
    return this.strategy;
  }

  public void replaceStrategy(final Strategy strategy) {
    this.series.replaceStrategy(strategy);
    this.strategy = strategy;
  }


  public boolean shouldEnter() {
    return this.strategy.shouldEnter();
  }

  public boolean shouldExit() {
    return this.strategy.shouldExit();
  }
}

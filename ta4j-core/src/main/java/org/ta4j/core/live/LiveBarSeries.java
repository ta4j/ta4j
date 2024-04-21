/**
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

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.num.NumFactory;

/**
 * Class deigned for use as live trading backing. Stores only current bar for analysis.
 *
 * Bar addition forces recalculation of all strategies.
 *
 * @author Lukáš Kvídera
 */
public class LiveBarSeries implements BarSeries {

  private final NumFactory numFactory;
  private final String name;
  private final BarBuilderFactory barBuilderFactory;

  private Bar bar;
  private Strategy strategy;


  LiveBarSeries(final String name, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory) {
    this.name = name;
    this.numFactory = numFactory;
    this.barBuilderFactory = barBuilderFactory;
  }


  @Override
  public NumFactory numFactory() {
    return this.numFactory;
  }


  @Override
  public LiveBarBuilder barBuilder() {
    return (LiveBarBuilder) this.barBuilderFactory.createBarBuilder(this);
  }


  @Override
  public String getName() {
    return this.name;
  }


  @Override
  public Bar getBar() {
    return this.bar;
  }


  @Override
  public void addBar(final Bar bar) {
     this.bar = bar;

     if (this.strategy != null) {
       this.strategy.refresh(bar.getEndTime());
     }
  }


  @Override
  public void replaceStrategy(final Strategy strategy) {
     this.strategy = strategy;
  }
}

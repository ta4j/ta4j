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

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.StrategyFactory;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@link BacktestBarSeries}.
 */
public class LiveTradingBuilder {

  /** The {@link #name} for an unnamed bar series. */
  private static final String UNNAMED_SERIES_NAME = "unnamed_series";

  private String name;
  private NumFactory numFactory = DecimalNumFactory.getInstance();
  private BarBuilderFactory barBuilderFactory = new LiveBarBuilderFactory();
  private StrategyFactory strategyFactory;


  /**
   * @param numFactory to set {@link BacktestBarSeries#numFactory()}
   *
   * @return {@code this}
   */
  public LiveTradingBuilder withNumFactory(final NumFactory numFactory) {
    this.numFactory = numFactory;
    return this;
  }


  /**
   * @param name to set {@link BacktestBarSeries#getName()}
   *
   * @return {@code this}
   */
  public LiveTradingBuilder withName(final String name) {
    this.name = name;
    return this;
  }


  /**
   * @param barBuilderFactory to build bars with the same datatype as series
   *
   * @return {@code this}
   */
  public LiveTradingBuilder withBarBuilderFactory(final BarBuilderFactory barBuilderFactory) {
    this.barBuilderFactory = barBuilderFactory;
    return this;
  }


  public LiveTradingBuilder withStrategyFactory(final StrategyFactory strategy) {
    this.strategyFactory = strategy;
    return this;
  }


  public LiveTrading build() {
    final var liveBarSeries = new LiveBarSeries(
        this.name == null ? UNNAMED_SERIES_NAME : this.name,
        this.numFactory,
        this.barBuilderFactory
    );

    if (this.strategyFactory == null) {
      throw new IllegalArgumentException("Strategy factory not set");
    }

    final var strategy = this.strategyFactory.createStrategy(liveBarSeries);
    liveBarSeries.replaceStrategy(strategy);

    return new LiveTrading(liveBarSeries, strategy);
  }
}

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
package org.ta4j.core.backtest;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.StrategyFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@link BacktestBarSeries}.
 */
public class BacktestBarSeriesBuilder {

    /** The {@link #name} for an unnamed bar series. */
    private static final String UNNAMED_SERIES_NAME = "unnamed_series";

    private List<BacktestBar> bars;
    private String name;
    private NumFactory numFactory = DecimalNumFactory.getInstance();
    private BarBuilderFactory barBuilderFactory = new BacktestBarBuilderFactory();
    private final List<StrategyFactory> strategyFactories = new ArrayList<>();

    /** Constructor to build a {@code BacktestBarSeries}. */
    public BacktestBarSeriesBuilder() {
        initValues();
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = "unnamed_series";
    }

    /**
     * @param numFactory to set {@link BacktestBarSeries#numFactory()}
     * @return {@code this}
     */
    public BacktestBarSeriesBuilder withNumFactory(final NumFactory numFactory) {
        this.numFactory = numFactory;
        return this;
    }

    /**
     * @param name to set {@link BacktestBarSeries#getName()}
     * @return {@code this}
     */
    public BacktestBarSeriesBuilder withName(final String name) {
        this.name = name;
        return this;
    }

    /**
     * @param bars to set {@link BacktestBarSeries#getBarData()}
     * @return {@code this}
     */
    public BacktestBarSeriesBuilder withBars(final List<BacktestBar> bars) {
        this.bars = bars;
        return this;
    }

    /**
     * @param barBuilderFactory to build bars with the same datatype as series
     *
     * @return {@code this}
     */
    public BacktestBarSeriesBuilder withBarBuilderFactory(final BarBuilderFactory barBuilderFactory) {
        this.barBuilderFactory = barBuilderFactory;
        return this;
    }

    public BacktestBarSeriesBuilder withStrategyFactory(final StrategyFactory strategy) {
        this.strategyFactories.add(strategy);
        return this;
    }

    public BacktestBarSeriesBuilder withStrategyFactories(
            final List<StrategyFactory> strategyFunction) {
      this.strategyFactories.addAll(strategyFunction);
        return this;
    }

    public BacktestBarSeries build() {
        final var series = new BacktestBarSeries(
            this.name == null ? UNNAMED_SERIES_NAME : this.name,
            this.numFactory,
            this.barBuilderFactory
        );

        series.replaceStrategies(
            this.strategyFactories.stream()
                .map(strategyFactory -> strategyFactory.createStrategy(series))
                .map(BacktestStrategy.class::cast)
                .toList()
        );
        this.bars.forEach(series::addBar);
        return series;
    }
}

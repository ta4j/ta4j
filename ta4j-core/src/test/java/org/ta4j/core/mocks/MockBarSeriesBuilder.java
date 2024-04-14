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
package org.ta4j.core.mocks;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.ta4j.core.BarSeries;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.backtest.BacktestBarSeriesBuilder;
import org.ta4j.core.backtest.BacktestStrategy;
import org.ta4j.core.num.NumFactory;

/**
 * Generates BacktestBar implementations with mocked time or duration if not set
 * by tester.
 */
public class MockBarSeriesBuilder extends BacktestBarSeriesBuilder {

    private List<Double> data;
    private boolean defaultData;
    private boolean strategy;

    public MockBarSeriesBuilder withNumFactory(final NumFactory numFactory) {
        super.withNumFactory(numFactory);
        return this;
    }

    /**
     * Generates bars with given close prices.
     *
     * @param data close prices
     * @return this
     */
    public MockBarSeriesBuilder withData(final List<Double> data) {
        this.data = data;
        return this;
    }

    /**
     * Generates bars with given close prices.
     *
     * @param data close prices
     * @return this
     */
    public MockBarSeriesBuilder withData(final double... data) {
        withData(DoubleStream.of(data).boxed().collect(Collectors.toList()));
        return this;
    }

    private static void doublesToBars(final BarSeries series, final List<Double> data) {
        for (int i = 0; i < data.size(); i++) {
            series.barBuilder().closePrice(data.get(i)).openPrice(0).add();
        }
    }

    public MockBarSeriesBuilder withDefaultData() {
        this.defaultData = true;
        return this;
    }

    public MockBarSeriesBuilder withStrategy(Function<BacktestBarSeries, BacktestStrategy> strategyFactory) {
        this.strategy = true;
        super.withStrategy(strategyFactory);
        return this;
    }

    private static void arbitraryBars(final BarSeries series) {
        for (double i = 0d; i < 5000; i++) {
            series.barBuilder()
                    .endTime(ZonedDateTime.now().minusMinutes((long) (5001 - i)))
                    .openPrice(i)
                    .closePrice(i + 1)
                    .highPrice(i + 2)
                    .lowPrice(i + 3)
                    .volume(i + 4)
                    .amount(i + 5)
                    .trades((int) (i + 6))
                    .add();
        }
    }

    @Override
    public BacktestBarSeries build() {
        withBarBuilderFactory(new MockBarBuilderFactory());
        if (!strategy) {
            withStrategy(x -> new MockStrategy(new MockRule(List.of())));
        }
        final var series = super.build();
        if (this.data != null) {
            doublesToBars(series, this.data);
        }
        if (this.defaultData) {
            arbitraryBars(series);
        }
        return series;
    }
}

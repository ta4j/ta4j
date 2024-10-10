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
package org.ta4j.core.mocks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * Generates BaseBar implementations with mocked time or duration if not set by
 * tester.
 */
public class MockBarSeriesBuilder extends BaseBarSeriesBuilder {

    private List<Double> data;
    private boolean defaultData;

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
        var now = Instant.now();
        for (int i = 0; i < data.size(); i++) {
            series.barBuilder()
                    .endTime(now.minus(Duration.ofMinutes((data.size() + 1 - i))))
                    .closePrice(data.get(i))
                    .openPrice(0)
                    .add();
        }
    }

    public MockBarSeriesBuilder withDefaultData() {
        this.defaultData = true;
        return this;
    }

    private static void arbitraryBars(final BarSeries series) {
        var now = Instant.now();
        for (double i = 0d; i < 5000; i++) {
            series.barBuilder()
                    .endTime(now.minus(Duration.ofMinutes((long) (5001 - i))))
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
    public BaseBarSeries build() {
        withBarBuilderFactory(new MockBarBuilderFactory());

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

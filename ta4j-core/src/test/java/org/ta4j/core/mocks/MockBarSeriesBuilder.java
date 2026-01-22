/*
 * SPDX-License-Identifier: MIT
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

    private static final Instant testStartTime = Instant.EPOCH;
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
        final var maxBars = data.size() + 1;
        for (int i = 0; i < data.size(); i++) {
            series.barBuilder()
                    .endTime(testStartTime.minus(Duration.ofMinutes(maxBars - i)))
                    .closePrice(data.get(i))
                    .openPrice(0)
                    .volume(0)
                    .amount(0)
                    .trades(0)
                    .add();
        }
    }

    public MockBarSeriesBuilder withDefaultData() {
        this.defaultData = true;
        return this;
    }

    private static void arbitraryBars(final BarSeries series) {
        for (double i = 0d; i < 5000; i++) {
            series.barBuilder()
                    .endTime(testStartTime.minus(Duration.ofMinutes((long) (5001 - i))))
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

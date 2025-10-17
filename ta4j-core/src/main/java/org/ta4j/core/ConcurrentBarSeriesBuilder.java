/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * Builder for {@link ConcurrentBarSeries} instances.
 *
 * @since 0.19
 */
public class ConcurrentBarSeriesBuilder implements BarSeriesBuilder {

    private static final String UNNAMED_SERIES_NAME = "unnamed_series";

    private List<Bar> bars;
    private String name;
    private boolean constrained;
    private int maxBarCount;
    private NumFactory numFactory = DecimalNumFactory.getInstance();
    private BarBuilderFactory barBuilderFactory = new TimeBarBuilderFactory();

    /**
     * Creates a builder for {@link ConcurrentBarSeries}.
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder() {
        initValues();
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = UNNAMED_SERIES_NAME;
        this.constrained = false;
        this.maxBarCount = Integer.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public ConcurrentBarSeries build() {
        int beginIndex = -1;
        int endIndex = -1;
        if (!bars.isEmpty()) {
            beginIndex = 0;
            endIndex = bars.size() - 1;
        }
        var series = new ConcurrentBarSeries(name == null ? UNNAMED_SERIES_NAME : name, bars, beginIndex, endIndex,
                constrained, numFactory, barBuilderFactory);
        series.setMaximumBarCount(maxBarCount);
        initValues();
        return series;
    }

    /**
     * @param constrained whether the resulting series is constrained
     * @return {@code this}
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder setConstrained(boolean constrained) {
        this.constrained = constrained;
        return this;
    }

    /**
     * @param numFactory {@link NumFactory} to back the series
     * @return {@code this}
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder withNumFactory(NumFactory numFactory) {
        this.numFactory = numFactory;
        return this;
    }

    /**
     * @param name name of the series
     * @return {@code this}
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param bars initial bars for the series
     * @return {@code this}
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder withBars(List<Bar> bars) {
        this.bars = new ArrayList<>(bars);
        return this;
    }

    /**
     * @param maxBarCount maximum retained bars
     * @return {@code this}
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder withMaxBarCount(int maxBarCount) {
        this.maxBarCount = maxBarCount;
        return this;
    }

    /**
     * @param barBuilderFactory builder factory for bars
     * @return {@code this}
     *
     * @since 0.19
     */
    public ConcurrentBarSeriesBuilder withBarBuilderFactory(BarBuilderFactory barBuilderFactory) {
        this.barBuilderFactory = barBuilderFactory;
        return this;
    }
}

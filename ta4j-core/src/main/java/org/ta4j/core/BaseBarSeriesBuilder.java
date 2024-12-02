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
package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.bars.BaseBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@link BaseBarSeries}.
 */
public class BaseBarSeriesBuilder implements BarSeriesBuilder {

    /** The {@link #name} for an unnamed bar series. */
    private static final String UNNAMED_SERIES_NAME = "unnamed_series";

    private List<Bar> bars;
    private String name;
    private boolean constrained;
    private int maxBarCount;
    private NumFactory numFactory = DecimalNumFactory.getInstance();
    private BarBuilderFactory barBuilderFactory = new BaseBarBuilderFactory();

    /** Constructor to build a {@code BaseBarSeries}. */
    public BaseBarSeriesBuilder() {
        initValues();
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = "unnamed_series";
        this.constrained = false;
        this.maxBarCount = Integer.MAX_VALUE;
    }

    @Override
    public BaseBarSeries build() {
        int beginIndex = -1;
        int endIndex = -1;
        if (!bars.isEmpty()) {
            beginIndex = 0;
            endIndex = bars.size() - 1;
        }

        var series = new BaseBarSeries(name == null ? UNNAMED_SERIES_NAME : name, bars, beginIndex, endIndex,
                constrained, numFactory, barBuilderFactory);
        series.setMaximumBarCount(maxBarCount);
        initValues(); // reinitialize values for next series
        return series;
    }

    /**
     * @param constrained to set
     * @return {@code this}
     */
    public BaseBarSeriesBuilder setConstrained(boolean constrained) {
        this.constrained = constrained;
        return this;
    }

    /**
     * @param numFactory to set {@link BaseBarSeries#numFactory()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withNumFactory(NumFactory numFactory) {
        this.numFactory = numFactory;
        return this;
    }

    /**
     * @param name to set {@link BaseBarSeries#getName()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param bars to set {@link BaseBarSeries#getBarData()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withBars(List<Bar> bars) {
        this.bars = bars;
        return this;
    }

    /**
     * @param maxBarCount to set {@link BaseBarSeries#getMaximumBarCount()}
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withMaxBarCount(int maxBarCount) {
        this.maxBarCount = maxBarCount;
        return this;
    }

    /**
     * @param barBuilderFactory to build bars with the same datatype as series
     *
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withBarBuilderFactory(final BarBuilderFactory barBuilderFactory) {
        this.barBuilderFactory = barBuilderFactory;
        return this;
    }
}

/*
 * SPDX-License-Identifier: MIT
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
 * @since 0.22.2
 */
public class ConcurrentBarSeriesBuilder implements BarSeriesBuilder {

    private static final String UNNAMED_SERIES_NAME = "unnamed_series";

    private List<Bar> bars;
    private String name;
    private boolean maxBarCountConfigured;
    private int maxBarCount;
    private NumFactory numFactory = DecimalNumFactory.getInstance();
    private BarBuilderFactory barBuilderFactory = new TimeBarBuilderFactory(true);

    /**
     * Creates a builder for {@link ConcurrentBarSeries}.
     *
     * @since 0.22.2
     */
    public ConcurrentBarSeriesBuilder() {
        initValues();
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = UNNAMED_SERIES_NAME;
        this.maxBarCountConfigured = false;
        this.maxBarCount = Integer.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.2
     */
    @Override
    public ConcurrentBarSeries build() {
        int beginIndex = -1;
        int endIndex = -1;
        if (!bars.isEmpty()) {
            beginIndex = 0;
            endIndex = bars.size() - 1;
        }
        // If maxBarCount is configured, the series must be unconstrained to allow
        // removals.
        boolean effectiveConstrained = !maxBarCountConfigured;
        var series = new ConcurrentBarSeries(name == null ? UNNAMED_SERIES_NAME : name, bars, beginIndex, endIndex,
                effectiveConstrained, numFactory, barBuilderFactory);
        if (maxBarCountConfigured) {
            series.setMaximumBarCount(maxBarCount);
        }
        initValues();
        return series;
    }

    /**
     * @param numFactory {@link NumFactory} to back the series
     * @return {@code this}
     *
     * @since 0.22.2
     */
    public ConcurrentBarSeriesBuilder withNumFactory(NumFactory numFactory) {
        this.numFactory = numFactory;
        return this;
    }

    /**
     * @param name name of the series
     * @return {@code this}
     *
     * @since 0.22.2
     */
    public ConcurrentBarSeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param bars initial bars for the series
     * @return {@code this}
     *
     * @since 0.22.2
     */
    public ConcurrentBarSeriesBuilder withBars(List<Bar> bars) {
        this.bars = new ArrayList<>(bars);
        return this;
    }

    /**
     * @param maxBarCount maximum retained bars (also opts the series into
     *                    pruning/unconstrained mode)
     * @return {@code this}
     *
     * @since 0.22.2
     */
    public ConcurrentBarSeriesBuilder withMaxBarCount(int maxBarCount) {
        this.maxBarCount = maxBarCount;
        this.maxBarCountConfigured = true;
        return this;
    }

    /**
     * @param barBuilderFactory builder factory for bars
     * @return {@code this}
     *
     * @since 0.22.2
     */
    public ConcurrentBarSeriesBuilder withBarBuilderFactory(BarBuilderFactory barBuilderFactory) {
        this.barBuilderFactory = barBuilderFactory;
        return this;
    }
}

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
 * A builder to build a new {@link BaseBarSeries}.
 */
public class BaseBarSeriesBuilder implements BarSeriesBuilder {

    /** The {@link #name} for an unnamed bar series. */
    private static final String UNNAMED_SERIES_NAME = "unnamed_series";

    private List<Bar> bars;
    private String name;
    private boolean constrained;
    private int maxBarCount;
    private boolean isNumFactoryAssigned = false;
    private NumFactory numFactory = DecimalNumFactory.getInstance();
    private BarBuilderFactory barBuilderFactory = new TimeBarBuilderFactory();

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

            if (!isNumFactoryAssigned) {
                // use numFactory derived from bars instead of default numFactory
                numFactory = bars.getFirst().numFactory();
            }

            // check if each bar has the same numFactory as the series numFactory
            for (var bar : bars) {
                if (bar.getClosePrice() != null) {
                    if (!numFactory.produces(bar.getClosePrice())) {
                        throw new IllegalArgumentException(
                                String.format("Cannot add Bar with data type: %s to series with datatype: %s",
                                        bar.getClosePrice().getClass(), this.numFactory.one().getClass()));
                    }
                }
            }

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
     *
     * @deprecated Constrained mode is being derived from max-bar-count
     *             configuration instead of being set directly. Prefer configuring
     *             retention via {@link #withMaxBarCount(int)} (or omit it for the
     *             default constrained behavior).
     */
    @Deprecated(since = "0.22.2")
    public BaseBarSeriesBuilder setConstrained(boolean constrained) {
        this.constrained = constrained;
        return this;
    }

    /**
     * @param numFactory to set {@link BaseBarSeries#numFactory()} (by default, uses
     *                   either {@link DecimalNumFactory} or {@code numFactory}
     *                   derived from {@link #bars})
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withNumFactory(NumFactory numFactory) {
        if (numFactory != null) {
            // user has explicitly assigned a numFactory
            isNumFactoryAssigned = true;
        }
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
     * @param bars to set {@link BaseBarSeries#getBarData()}; If {@link #numFactory}
     *             is not assigned by {@link #withNumFactory(NumFactory)},
     *             {@link #numFactory} defaults to the {@code numFactory} of the
     *             {@code bars}.
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
     * @param barBuilderFactory to build bars with the same datatype as series (by
     *                          default, uses {@link TimeBarBuilderFactory})
     *
     * @return {@code this}
     */
    public BaseBarSeriesBuilder withBarBuilderFactory(final BarBuilderFactory barBuilderFactory) {
        this.barBuilderFactory = barBuilderFactory;
        return this;
    }

}

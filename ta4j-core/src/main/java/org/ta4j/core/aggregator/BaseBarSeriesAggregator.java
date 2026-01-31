/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

/**
 * Aggregates a {@link BaseBarSeries} into another one using a
 * {@link BarAggregator}.
 */
public class BaseBarSeriesAggregator implements BarSeriesAggregator {

    private final BarAggregator barAggregator;

    /**
     * Constructor.
     *
     * @param barAggregator the {@link BarAggregator}
     */
    public BaseBarSeriesAggregator(BarAggregator barAggregator) {
        this.barAggregator = barAggregator;
    }

    @Override
    public BarSeries aggregate(BarSeries series, String aggregatedSeriesName) {
        final List<Bar> aggregatedBars = barAggregator.aggregate(series.getBarData());
        return new BaseBarSeriesBuilder().withName(aggregatedSeriesName).withBars(aggregatedBars).build();
    }
}

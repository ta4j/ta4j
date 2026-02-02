/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import org.ta4j.core.BarSeries;

/**
 * Aggregates a {@link BarSeries} into another one.
 */
public interface BarSeriesAggregator {

    /**
     * Aggregates the {@code series} into another one.
     *
     * @param series the series to be aggregated
     * @return aggregated series
     */
    default BarSeries aggregate(BarSeries series) {
        return aggregate(series, series.getName());
    }

    /**
     * Aggregates the {@code series} into another one.
     *
     * @param series               the series to be aggregated
     * @param aggregatedSeriesName the name for the aggregated series
     * @return aggregated series with specified name
     */
    BarSeries aggregate(BarSeries series, String aggregatedSeriesName);
}

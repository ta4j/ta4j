package org.ta4j.core.aggregator;

import org.ta4j.core.TimeSeries;

/**
 * Bar aggregator interface to aggregate list of bars into another list of bars.
 */
public interface TimeSeriesAggregator {

    /**
     * Aggregate time series.
     *
     * @param series series to aggregate
     * @return aggregated series
     */
    default TimeSeries aggregate(TimeSeries series) {
        return aggregate(series, series.getName());
    }

    /**
     * Aggregate time series.
     *
     * @param series               series to aggregate
     * @param aggregatedSeriesName name for aggregated series
     * @return aggregated series with specified name
     */
    TimeSeries aggregate(TimeSeries series, String aggregatedSeriesName);
}

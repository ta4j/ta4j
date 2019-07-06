package org.ta4j.core.aggregator;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;

import java.util.List;

/**
 * Time series aggregator based on provided bar aggregator.
 */
public class BaseTimeSeriesAggregator implements TimeSeriesAggregator {

    private final BarAggregator barAggregator;

    public BaseTimeSeriesAggregator(BarAggregator barAggregator) {
        this.barAggregator = barAggregator;
    }

    @Override
    public TimeSeries aggregate(TimeSeries series, String aggregatedSeriesName) {
        final List<Bar> aggregatedBars = barAggregator.aggregate(series.getBarData());
        return new BaseTimeSeries(aggregatedSeriesName, aggregatedBars);
    }
}

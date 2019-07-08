package org.ta4j.core.aggregator;

import org.ta4j.core.Bar;

import java.util.List;

/**
 * Bar aggregator interface to aggregate list of bars into another list of bars.
 */
public interface BarAggregator {

    /**
     * Aggregate bars.
     *
     * @param bars bars to aggregate bars
     * @return aggregated bars
     */
    List<Bar> aggregate(List<Bar> bars);
}

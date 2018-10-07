package org.ta4j.core;


import java.io.Serializable;

/**
 * Interface to build a time series
 */
public interface TimeSeriesBuilder extends Serializable {
    /**
     * Builds the time series with corresponding parameters
     * @return
     */
    TimeSeries build();
}

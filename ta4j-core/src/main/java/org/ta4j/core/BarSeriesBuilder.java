/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

/**
 * Interface to build a bar series.
 */
public interface BarSeriesBuilder {

    /**
     * Builds the bar series with corresponding parameters.
     *
     * @return bar series
     */
    BarSeries build();
}

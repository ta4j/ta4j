/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

/**
 * Interface to build a {@link BarSeries}.
 *
 * <p>
 * Use {@link org.ta4j.core.BaseBarSeriesBuilder} for deterministic
 * single-threaded workflows, and
 * {@link org.ta4j.core.ConcurrentBarSeriesBuilder} when ingestion and
 * evaluation may happen concurrently.
 * </p>
 */
public interface BarSeriesBuilder {

    /**
     * Builds the bar series with corresponding parameters.
     *
     * @return bar series
     */
    BarSeries build();
}

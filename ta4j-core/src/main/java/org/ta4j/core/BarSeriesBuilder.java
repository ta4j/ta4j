/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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

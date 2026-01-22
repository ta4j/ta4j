/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.aggregator;

import java.util.List;

import org.ta4j.core.Bar;

/**
 * Aggregates a list of {@link Bar bars} into another one.
 */
public interface BarAggregator {

    /**
     * Aggregates the {@code bars} into another one.
     *
     * @param bars the bars to be aggregated
     * @return aggregated bars
     */
    List<Bar> aggregate(List<Bar> bars);
}

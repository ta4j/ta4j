/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core;

/**
 * A factory that provides a builder for a bar.
 */
public interface BarBuilderFactory {

    /**
     * Constructor.
     *
     * @param series the bar series to which the created bar should be added
     * @return the bar builder
     */
    BarBuilder createBarBuilder(BarSeries series);
}

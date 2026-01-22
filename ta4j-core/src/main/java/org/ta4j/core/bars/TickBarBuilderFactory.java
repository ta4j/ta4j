/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

public class TickBarBuilderFactory implements BarBuilderFactory {

    private final int tickCount;
    private TickBarBuilder barBuilder;

    /**
     * Constructor.
     *
     * @param tickCount the number of ticks at which a new bar should be created
     */
    public TickBarBuilderFactory(final int tickCount) {
        this.tickCount = tickCount;
    }

    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        if (this.barBuilder == null) {
            this.barBuilder = new TickBarBuilder(series.numFactory(), this.tickCount).bindTo(series);
        }

        return this.barBuilder;
    }
}

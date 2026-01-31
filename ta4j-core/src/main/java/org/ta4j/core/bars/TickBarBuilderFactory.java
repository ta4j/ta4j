/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

public class TickBarBuilderFactory implements BarBuilderFactory {

    private final boolean realtimeBars;
    private final int tickCount;
    private transient TickBarBuilder barBuilder;

    /**
     * Constructor.
     *
     * @param tickCount the number of ticks at which a new bar should be created
     */
    public TickBarBuilderFactory(final int tickCount) {
        this(tickCount, false);
    }

    /**
     * Constructor.
     *
     * @param tickCount    the number of ticks at which a new bar should be created
     * @param realtimeBars {@code true} to build {@link BaseRealtimeBar} instances
     *
     * @since 0.22.2
     */
    public TickBarBuilderFactory(final int tickCount, final boolean realtimeBars) {
        this.tickCount = tickCount;
        this.realtimeBars = realtimeBars;
    }

    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        if (this.barBuilder == null) {
            this.barBuilder = new TickBarBuilder(series.numFactory(), this.tickCount, this.realtimeBars).bindTo(series);
        }

        return this.barBuilder;
    }
}

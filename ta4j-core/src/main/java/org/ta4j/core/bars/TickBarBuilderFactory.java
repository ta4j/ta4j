/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

public class TickBarBuilderFactory implements BarBuilderFactory {

    private static final long serialVersionUID = 1L;

    private final boolean realtimeBars;
    private final int tickCount;
    private transient Map<BarSeries, TickBarBuilder> barBuilders;

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
    public synchronized BarBuilder createBarBuilder(final BarSeries series) {
        final BarSeries requestedSeries = Objects.requireNonNull(series);
        return builders().computeIfAbsent(requestedSeries, this::createBoundBuilder);
    }

    private Map<BarSeries, TickBarBuilder> builders() {
        if (barBuilders == null) {
            barBuilders = new IdentityHashMap<>();
        }
        return barBuilders;
    }

    private TickBarBuilder createBoundBuilder(final BarSeries series) {
        return new TickBarBuilder(series.numFactory(), this.tickCount, this.realtimeBars).bindTo(series);
    }
}

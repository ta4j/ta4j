/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Fail-fast validation of tick count is a documented constructor contract; subclassing remains supported.")
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
        if (tickCount <= 0) {
            throw new IllegalArgumentException("tickCount must be positive");
        }
        this.tickCount = tickCount;
        this.realtimeBars = realtimeBars;
    }

    @Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if (tickCount <= 0) {
            throw new InvalidObjectException("tickCount must be positive");
        }
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

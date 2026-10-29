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

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

public class AmountBarBuilderFactory implements BarBuilderFactory {

    private static final long serialVersionUID = 1L;

    private final int amountThreshold;
    private final boolean setAmountByVolume;
    private final RemainderCarryOverPolicy carryOverPolicy;
    private final boolean realtimeBars;
    private transient Map<BarSeries, AmountBarBuilder> barBuilders;

    /**
     * Constructor.
     *
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@code amount} is set by
     *                          {@code volume * closePrice}, otherwise
     *                          {@code amount} must be explicitly set
     */
    public AmountBarBuilderFactory(final int amountThreshold, final boolean setAmountByVolume) {
        this(amountThreshold, setAmountByVolume, false, RemainderCarryOverPolicy.NONE);
    }

    /**
     * Constructor.
     *
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@code amount} is set by
     *                          {@code volume * closePrice}, otherwise
     *                          {@code amount} must be explicitly set
     * @param realtimeBars      {@code true} to build {@link BaseRealtimeBar}
     *                          instances
     *
     * @since 0.22.2
     */
    public AmountBarBuilderFactory(final int amountThreshold, final boolean setAmountByVolume,
            final boolean realtimeBars) {
        this(amountThreshold, setAmountByVolume, realtimeBars, RemainderCarryOverPolicy.NONE);
    }

    /**
     * Constructor.
     *
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@code amount} is set by
     *                          {@code volume * closePrice}, otherwise
     *                          {@code amount} must be explicitly set
     * @param carryOverPolicy   policy for handling side/liquidity remainder splits
     *
     * @since 0.22.2
     */
    public AmountBarBuilderFactory(final int amountThreshold, final boolean setAmountByVolume,
            final RemainderCarryOverPolicy carryOverPolicy) {
        this(amountThreshold, setAmountByVolume, false, carryOverPolicy);
    }

    /**
     * Constructor.
     *
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@code amount} is set by
     *                          {@code volume * closePrice}, otherwise
     *                          {@code amount} must be explicitly set
     * @param realtimeBars      {@code true} to build {@link BaseRealtimeBar}
     *                          instances
     * @param carryOverPolicy   policy for handling side/liquidity remainder splits
     *
     * @since 0.22.2
     */
    public AmountBarBuilderFactory(final int amountThreshold, final boolean setAmountByVolume,
            final boolean realtimeBars, final RemainderCarryOverPolicy carryOverPolicy) {
        if (amountThreshold <= 0) {
            throw new IllegalArgumentException("amountThreshold must be positive");
        }
        this.amountThreshold = amountThreshold;
        this.setAmountByVolume = setAmountByVolume;
        this.realtimeBars = realtimeBars;
        this.carryOverPolicy = carryOverPolicy == null ? RemainderCarryOverPolicy.NONE : carryOverPolicy;
    }

    @Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if (amountThreshold <= 0) {
            throw new InvalidObjectException("amountThreshold must be positive");
        }
        if (carryOverPolicy == null) {
            throw new InvalidObjectException("carryOverPolicy must not be null");
        }
    }

    @Override
    public synchronized BarBuilder createBarBuilder(final BarSeries series) {
        final BarSeries requestedSeries = Objects.requireNonNull(series);
        return builders().computeIfAbsent(requestedSeries, this::createBoundBuilder);
    }

    private Map<BarSeries, AmountBarBuilder> builders() {
        if (barBuilders == null) {
            barBuilders = new IdentityHashMap<>();
        }
        return barBuilders;
    }

    private AmountBarBuilder createBoundBuilder(final BarSeries series) {
        return new AmountBarBuilder(series.numFactory(), this.amountThreshold, this.setAmountByVolume,
                this.realtimeBars, this.carryOverPolicy).bindTo(series);
    }
}

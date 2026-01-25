/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

public class AmountBarBuilderFactory implements BarBuilderFactory {

    private final int amountThreshold;
    private final boolean setAmountByVolume;
    private final RemainderCarryOverPolicy carryOverPolicy;
    private final boolean realtimeBars;
    private transient AmountBarBuilder barBuilder;

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
        this.amountThreshold = amountThreshold;
        this.setAmountByVolume = setAmountByVolume;
        this.realtimeBars = realtimeBars;
        this.carryOverPolicy = carryOverPolicy == null ? RemainderCarryOverPolicy.NONE : carryOverPolicy;
    }

    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        if (this.barBuilder == null) {
            this.barBuilder = new AmountBarBuilder(series.numFactory(), this.amountThreshold, this.setAmountByVolume,
                    this.realtimeBars, this.carryOverPolicy).bindTo(series);
        }

        return this.barBuilder;
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

public class VolumeBarBuilderFactory implements BarBuilderFactory {

    private final int volumeThreshold;
    private final RemainderCarryOverPolicy carryOverPolicy;
    private final boolean realtimeBars;
    private transient VolumeBarBuilder barBuilder;

    /**
     * Constructor.
     *
     * @param volumeThreshold the threshold at which a new bar should be created
     */
    public VolumeBarBuilderFactory(final int volumeThreshold) {
        this(volumeThreshold, false, RemainderCarryOverPolicy.NONE);
    }

    /**
     * Constructor.
     *
     * @param volumeThreshold the threshold at which a new bar should be created
     * @param realtimeBars    {@code true} to build {@link BaseRealtimeBar}
     *                        instances
     *
     * @since 0.22.2
     */
    public VolumeBarBuilderFactory(final int volumeThreshold, final boolean realtimeBars) {
        this(volumeThreshold, realtimeBars, RemainderCarryOverPolicy.NONE);
    }

    /**
     * Constructor.
     *
     * @param volumeThreshold the threshold at which a new bar should be created
     * @param carryOverPolicy policy for handling side/liquidity remainder splits
     *
     * @since 0.22.0
     */
    public VolumeBarBuilderFactory(final int volumeThreshold, final RemainderCarryOverPolicy carryOverPolicy) {
        this(volumeThreshold, false, carryOverPolicy);
    }

    /**
     * Constructor.
     *
     * @param volumeThreshold the threshold at which a new bar should be created
     * @param realtimeBars    {@code true} to build {@link BaseRealtimeBar}
     *                        instances
     * @param carryOverPolicy policy for handling side/liquidity remainder splits
     *
     * @since 0.22.2
     */
    public VolumeBarBuilderFactory(final int volumeThreshold, final boolean realtimeBars,
            final RemainderCarryOverPolicy carryOverPolicy) {
        this.volumeThreshold = volumeThreshold;
        this.realtimeBars = realtimeBars;
        this.carryOverPolicy = carryOverPolicy == null ? RemainderCarryOverPolicy.NONE : carryOverPolicy;
    }

    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        if (this.barBuilder == null) {
            this.barBuilder = new VolumeBarBuilder(series.numFactory(), this.volumeThreshold, this.realtimeBars,
                    this.carryOverPolicy).bindTo(series);
        }

        return this.barBuilder;
    }
}

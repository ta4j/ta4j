/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
    private VolumeBarBuilder barBuilder;

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
     * @since 0.22.0
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
     * @since 0.22.0
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

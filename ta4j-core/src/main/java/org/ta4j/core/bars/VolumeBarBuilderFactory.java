/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

public class VolumeBarBuilderFactory implements BarBuilderFactory {

    private final int volumeThreshold;
    private VolumeBarBuilder barBuilder;

    /**
     * Constructor.
     *
     * @param volumeThreshold the threshold at which a new bar should be created
     */
    public VolumeBarBuilderFactory(final int volumeThreshold) {
        this.volumeThreshold = volumeThreshold;
    }

    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        if (this.barBuilder == null) {
            this.barBuilder = new VolumeBarBuilder(series.numFactory(), this.volumeThreshold).bindTo(series);
        }

        return this.barBuilder;
    }
}

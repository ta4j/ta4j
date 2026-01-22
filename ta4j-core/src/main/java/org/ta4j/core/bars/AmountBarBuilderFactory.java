/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.bars;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;

public class AmountBarBuilderFactory implements BarBuilderFactory {

    private final int amountThreshold;
    private final boolean setAmountByVolume;
    private AmountBarBuilder barBuilder;

    /**
     * Constructor.
     *
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@code amount} is set by
     *                          {@code volume * closePrice}, otherwise
     *                          {@code amount} must be explicitly set
     */
    public AmountBarBuilderFactory(final int amountThreshold, final boolean setAmountByVolume) {
        this.amountThreshold = amountThreshold;
        this.setAmountByVolume = setAmountByVolume;
    }

    @Override
    public BarBuilder createBarBuilder(final BarSeries series) {
        if (this.barBuilder == null) {
            this.barBuilder = new AmountBarBuilder(series.numFactory(), this.amountThreshold, this.setAmountByVolume)
                    .bindTo(series);
        }

        return this.barBuilder;
    }
}

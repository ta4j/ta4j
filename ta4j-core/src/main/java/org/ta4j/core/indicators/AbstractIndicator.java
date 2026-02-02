/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Abstract {@link Indicator indicator}.
 */
public abstract class AbstractIndicator<T> implements Indicator<T> {

    /** The logger. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final BarSeries series;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected AbstractIndicator(BarSeries series) {
        this.series = series;
    }

    @Override
    public BarSeries getBarSeries() {
        return series;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}

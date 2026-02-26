/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import org.ta4j.core.BarSeries;

/**
 * Selects a series window or transformed series for one-shot analysis.
 *
 * @param <C> selector context type (for example degree, timeframe, or offset)
 * @since 0.22.3
 */
@FunctionalInterface
public interface SeriesSelector<C> {

    /**
     * Selects the series to analyze for the supplied context.
     *
     * @param series  root input series
     * @param context caller-provided selector context
     * @return selected series (may be a subseries or transformed series)
     * @since 0.22.3
     */
    BarSeries select(BarSeries series, C context);
}

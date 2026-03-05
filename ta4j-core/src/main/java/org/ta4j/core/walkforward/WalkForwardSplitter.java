/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;

import org.ta4j.core.BarSeries;

/**
 * Produces walk-forward splits for a bar series and configuration.
 *
 * @since 0.22.4
 */
@FunctionalInterface
public interface WalkForwardSplitter {

    /**
     * Builds ordered walk-forward folds for the provided series.
     *
     * @param series series to split
     * @param config walk-forward configuration
     * @return ordered split definitions
     * @since 0.22.4
     */
    List<WalkForwardSplit> split(BarSeries series, WalkForwardConfig config);
}

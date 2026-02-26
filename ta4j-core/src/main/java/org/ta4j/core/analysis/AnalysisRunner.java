/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import org.ta4j.core.BarSeries;

/**
 * Executes a one-shot analysis for a series and caller-provided context.
 *
 * @param <C> analysis context type (for example degree, configuration, or
 *            request object)
 * @param <R> analysis result type
 * @since 0.22.3
 */
@FunctionalInterface
public interface AnalysisRunner<C, R> {

    /**
     * Runs analysis for the supplied series and context.
     *
     * @param series  series to analyze
     * @param context caller-provided analysis context
     * @return analysis result
     * @since 0.22.3
     */
    R analyze(BarSeries series, C context);
}

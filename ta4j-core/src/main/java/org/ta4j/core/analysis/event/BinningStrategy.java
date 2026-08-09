/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

/**
 * Predictor discretization strategy for
 * {@link EventMutualInformationEvaluator}.
 *
 * @since 0.24.1
 */
public enum BinningStrategy {

    /**
     * Divide the predictor range into bins of equal width, matching the binning of
     * {@code MutualInformationIndicator}. Simple, but skewed distributions can
     * crowd most samples into a few bins.
     */
    EQUAL_WIDTH,
    /**
     * Divide the sorted predictor samples into bins of approximately equal count.
     * Identical predictor values are never split across bins, so the effective bin
     * count may be smaller than requested; it is reported in the result.
     */
    EQUAL_FREQUENCY
}

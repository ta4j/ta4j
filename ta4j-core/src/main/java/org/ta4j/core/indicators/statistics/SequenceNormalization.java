/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

/**
 * Sequence normalization for {@link DynamicTimeWarpingDistanceIndicator}.
 *
 * <p>
 * {@link #Z_SCORE} removes level and scale so the warped distance measures
 * shape only. Under z-score normalization a zero-standard-deviation sequence is
 * mapped to zeros, so two constant sequences have zero shape distance
 * regardless of level, and a constant sequence compared with a varying one
 * measures the varying normalized shape against zeros. Callers who care about
 * absolute level use {@link #NONE}.
 * </p>
 *
 * @since 0.24.1
 */
public enum SequenceNormalization {

    /**
     * No normalization; distances are computed on the raw values.
     */
    NONE,
    /**
     * Standardize each sequence to zero mean and unit standard deviation before
     * computing distances.
     */
    Z_SCORE
}

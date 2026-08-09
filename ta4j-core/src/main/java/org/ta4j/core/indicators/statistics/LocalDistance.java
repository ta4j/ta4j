/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

/**
 * Local distance between two aligned samples of
 * {@link DynamicTimeWarpingDistanceIndicator}.
 *
 * @since 0.24.1
 */
public enum LocalDistance {

    /**
     * Absolute difference between the two samples.
     */
    ABSOLUTE,
    /**
     * Squared difference between the two samples; penalizes large local deviations
     * more strongly.
     */
    SQUARED
}

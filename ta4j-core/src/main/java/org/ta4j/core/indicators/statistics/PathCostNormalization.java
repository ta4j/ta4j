/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

/**
 * How the accumulated path cost of {@link DynamicTimeWarpingDistanceIndicator}
 * is turned into the reported distance.
 *
 * @since 0.24.1
 */
public enum PathCostNormalization {

    /**
     * Report the raw accumulated cost of the optimal warping path.
     */
    NONE,
    /**
     * Divide the accumulated cost by the number of cells on the optimal path so
     * that paths of different lengths remain comparable.
     */
    BY_PATH_LENGTH
}

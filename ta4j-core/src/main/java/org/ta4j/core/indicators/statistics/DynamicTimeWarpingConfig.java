/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

/**
 * Immutable configuration for {@link DynamicTimeWarpingDistanceIndicator}.
 *
 * <p>
 * The defaults are shape-oriented: z-score normalization, squared local
 * distance, a bounded Sakoe–Chiba band, and path-length normalization.
 * </p>
 *
 * @param normalization         sequence normalization before distance
 *                              computation
 * @param localDistance         pointwise distance between aligned samples
 * @param warpingWindow         allowed alignment band
 * @param pathCostNormalization how the accumulated path cost is normalized
 * @since 0.24.1
 */
public record DynamicTimeWarpingConfig(SequenceNormalization normalization, LocalDistance localDistance,
        WarpingWindow warpingWindow, PathCostNormalization pathCostNormalization) {

    /**
     * Validates the configuration.
     *
     * @throws NullPointerException if any component is null
     */
    public DynamicTimeWarpingConfig {
        Objects.requireNonNull(normalization, "normalization");
        Objects.requireNonNull(localDistance, "localDistance");
        Objects.requireNonNull(warpingWindow, "warpingWindow");
        Objects.requireNonNull(pathCostNormalization, "pathCostNormalization");
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Immutable representation of a single Elliott swing between two pivots.
 *
 * <p>
 * Swings are produced by {@link ElliottSwingIndicator} and form the foundation
 * for wave counting, phase classification, and scenario generation. Use this
 * record when you need to reason about pivot-to-pivot movement or annotate
 * swings on charts.
 *
 * @since 0.22.0
 */
public record ElliottSwing(int fromIndex, int toIndex, Num fromPrice, Num toPrice, ElliottDegree degree) {

    public ElliottSwing {
        if (fromIndex < 0 || toIndex < 0) {
            throw new IllegalArgumentException("Swing indices must be non-negative");
        }
        if (fromIndex == toIndex) {
            throw new IllegalArgumentException("Swing indices must be different");
        }
        Objects.requireNonNull(fromPrice, "fromPrice");
        Objects.requireNonNull(toPrice, "toPrice");
        Objects.requireNonNull(degree, "degree");
    }

    /**
     * @return {@code true} if the swing is rising from the start pivot to the end
     *         pivot
     * @since 0.22.0
     */
    public boolean isRising() {
        return !toPrice.isLessThan(fromPrice);
    }

    /**
     * @return the absolute price displacement between both pivots
     * @since 0.22.0
     */
    public Num amplitude() {
        return toPrice.minus(fromPrice).abs();
    }

    /**
     * @return number of bars covered by the swing
     * @since 0.22.0
     */
    public int length() {
        return Math.abs(toIndex - fromIndex);
    }
}

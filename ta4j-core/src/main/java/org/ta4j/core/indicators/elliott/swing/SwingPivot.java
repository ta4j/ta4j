/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Represents a confirmed swing pivot.
 *
 * @param index bar index of the pivot
 * @param price pivot price level
 * @param type  pivot type (high/low)
 * @since 0.22.2
 */
public record SwingPivot(int index, Num price, SwingPivotType type) {

    public SwingPivot {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(type, "type");
    }
}

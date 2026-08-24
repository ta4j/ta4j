/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.Objects;

import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.Num;

/**
 * Degree-free swing pivot carrying causal confirmation provenance.
 *
 * <p>
 * Experimental topology-kernel input type: {@code pivotIndex} is the bar index
 * of the detected extreme, {@code confirmationIndex} is the first bar index at
 * which the pivot was visible as confirmed. No state derived from this pivot
 * may be consumed before {@code confirmationIndex}.
 *
 * @param pivotIndex        bar index of the pivot extreme
 * @param confirmationIndex first bar index at which the pivot was confirmed
 * @param price             pivot price level
 * @param type              pivot type (high/low)
 */
record ConfirmedPivot(int pivotIndex, int confirmationIndex, Num price, SwingPivotType type) {

    ConfirmedPivot {
        if (pivotIndex < 0) {
            throw new IllegalArgumentException("pivotIndex must be non-negative");
        }
        if (confirmationIndex < pivotIndex) {
            throw new IllegalArgumentException("confirmationIndex must not precede pivotIndex");
        }
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(type, "type");
    }

    boolean confirmedAt(final int barIndex) {
        return confirmationIndex <= barIndex;
    }
}

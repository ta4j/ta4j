/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

/**
 * One matched predicted-reference event pair.
 *
 * <p>
 * The signed lag is derived, never stored: {@link #offsetBars()} returns
 * {@code referenceIndex - predictedIndex}, so no redundant invalid state can be
 * constructed.
 *
 * @param predictedIndex the bar index of the predicted event, {@code >= 0}
 * @param referenceIndex the bar index of the reference event, {@code >= 0}
 * @since 0.24.2
 */
public record EventMatch(int predictedIndex, int referenceIndex) {

    /**
     * Validates that both event indexes are nonnegative.
     */
    public EventMatch {
        if (predictedIndex < 0) {
            throw new IllegalArgumentException("predictedIndex must be >= 0, but was " + predictedIndex);
        }
        if (referenceIndex < 0) {
            throw new IllegalArgumentException("referenceIndex must be >= 0, but was " + referenceIndex);
        }
    }

    /**
     * @return the signed lag {@code referenceIndex - predictedIndex}; positive
     *         means the prediction leads the reference, zero exact coincidence,
     *         negative means the prediction lags the reference
     */
    public int offsetBars() {
        return referenceIndex - predictedIndex;
    }
}

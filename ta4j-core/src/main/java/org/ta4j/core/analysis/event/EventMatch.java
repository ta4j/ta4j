/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

/**
 * One matched predicted-reference event pair.
 *
 * @param predictedIndex the bar index of the predicted event
 * @param referenceIndex the bar index of the reference event
 * @param offsetBars     the signed lag {@code referenceIndex - predictedIndex}
 * @since 0.24.1
 */
public record EventMatch(int predictedIndex, int referenceIndex, int offsetBars) {

    /**
     * Validates the signed-lag invariant.
     */
    public EventMatch {
        if (offsetBars != referenceIndex - predictedIndex) {
            throw new IllegalArgumentException(
                    "offsetBars must equal referenceIndex - predictedIndex, but was " + offsetBars);
        }
    }
}

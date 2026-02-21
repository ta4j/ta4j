/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.util.Objects;

/**
 * Describes the inferred Wyckoff phase for a bar.
 *
 * @param cycleType        the broader Wyckoff cycle (accumulation or
 *                         distribution)
 * @param phaseType        the lettered Wyckoff phase (A-E)
 * @param confidence       confidence score between {@code 0.0} and {@code 1.0}
 * @param latestEventIndex index of the latest structural event backing the
 *                         inference, or {@code -1} if none
 *
 * @since 0.22.3
 */
public record WyckoffPhase(WyckoffCycleType cycleType, WyckoffPhaseType phaseType, double confidence,
        int latestEventIndex) {

    /**
     * Constant describing an indeterminate phase with zero confidence.
     *
     * <p>
     * {@link WyckoffPhaseType#PHASE_A} acts as a placeholder because
     * {@link WyckoffPhaseType} does not define an explicit unknown value.
     */
    public static final WyckoffPhase UNKNOWN = new WyckoffPhase(WyckoffCycleType.UNKNOWN, WyckoffPhaseType.PHASE_A, 0.0,
            -1);

    /**
     * Validates record invariants.
     *
     * @throws IllegalArgumentException if confidence is NaN, infinite, or outside
     *                                  {@code [0.0, 1.0]}
     * @throws NullPointerException     if cycle or phase is {@code null}
     */
    public WyckoffPhase {
        cycleType = Objects.requireNonNull(cycleType, "cycleType");
        phaseType = Objects.requireNonNull(phaseType, "phaseType");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be finite and between 0.0 and 1.0");
        }
    }

    /**
     * Returns a new phase with the provided confidence value.
     *
     * @param confidenceValue the confidence to apply
     * @return a copy with the updated confidence
     * @since 0.22.3
     */
    public WyckoffPhase withConfidence(double confidenceValue) {
        return new WyckoffPhase(cycleType, phaseType, confidenceValue, latestEventIndex);
    }

    /**
     * Returns a new phase that records the supplied latest event index.
     *
     * @param eventIndex index of the latest structural event
     * @return a copy pointing to the supplied event index
     * @since 0.22.3
     */
    public WyckoffPhase withLatestEventIndex(int eventIndex) {
        return new WyckoffPhase(cycleType, phaseType, confidence, eventIndex);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

/**
 * Enumerates the lettered Wyckoff phases within a market cycle.
 *
 * @since 0.22.2
 */
public enum WyckoffPhaseType {

    /** Phase A: stopping action. */
    PHASE_A('A'),

    /** Phase B: building a cause. */
    PHASE_B('B'),

    /** Phase C: testing. */
    PHASE_C('C'),

    /** Phase D: trend confirmation. */
    PHASE_D('D'),

    /** Phase E: trend in motion. */
    PHASE_E('E');

    private final char letter;

    WyckoffPhaseType(char letter) {
        this.letter = letter;
    }

    /**
     * Returns the canonical phase letter.
     *
     * @return the letter representing the phase
     * @since 0.22.2
     */
    public char getLetter() {
        return letter;
    }
}

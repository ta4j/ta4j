/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

/**
 * Categorizes Elliott wave structure patterns.
 *
 * @since 0.22.0
 */
public enum ScenarioType {

    /**
     * Standard five-wave motive structure (waves 1-2-3-4-5).
     */
    IMPULSE,

    /**
     * Three-wave corrective pattern with sharp moves (A-B-C where C exceeds A).
     */
    CORRECTIVE_ZIGZAG,

    /**
     * Three-wave corrective pattern where waves A and C are approximately equal and
     * wave B retraces most of wave A.
     */
    CORRECTIVE_FLAT,

    /**
     * Five-wave corrective pattern forming a contracting or expanding triangle
     * (A-B-C-D-E).
     */
    CORRECTIVE_TRIANGLE,

    /**
     * Combination of multiple corrective patterns (double/triple zigzag, double
     * three, etc.).
     */
    CORRECTIVE_COMPLEX,

    /**
     * Pattern type could not be determined.
     */
    UNKNOWN;

    /**
     * @return {@code true} if this is a motive (impulse) pattern
     * @since 0.22.0
     */
    public boolean isImpulse() {
        return this == IMPULSE;
    }

    /**
     * @return {@code true} if this is any corrective pattern type
     * @since 0.22.0
     */
    public boolean isCorrective() {
        return this == CORRECTIVE_ZIGZAG || this == CORRECTIVE_FLAT || this == CORRECTIVE_TRIANGLE
                || this == CORRECTIVE_COMPLEX;
    }

    /**
     * @return expected wave count for this pattern type (5 for impulse, 3 for
     *         zigzag/flat, 5 for triangle, 0 for unknown/complex)
     * @since 0.22.0
     */
    public int expectedWaveCount() {
        return switch (this) {
        case IMPULSE -> 5;
        case CORRECTIVE_ZIGZAG, CORRECTIVE_FLAT -> 3;
        case CORRECTIVE_TRIANGLE -> 5;
        default -> 0;
        };
    }
}

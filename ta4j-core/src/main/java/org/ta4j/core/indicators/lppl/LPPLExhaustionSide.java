/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

/**
 * Direction of a Log-Periodic Power Law (LPPL) exhaustion signal.
 *
 * <p>
 * The sign convention matches capital-rotation dashboards: positive values
 * identify crash exhaustion after persistent downside pressure, while negative
 * values identify bubble exhaustion after persistent upside pressure.
 *
 * @since 0.23.1
 */
public enum LPPLExhaustionSide {

    /**
     * No dominant LPPL exhaustion side was detected.
     */
    NONE(0),

    /**
     * Downside LPPL exhaustion. Numeric scores are positive.
     */
    CRASH_EXHAUSTION(1),

    /**
     * Upside LPPL exhaustion. Numeric scores are negative.
     */
    BUBBLE_EXHAUSTION(-1);

    private final int scoreSign;

    LPPLExhaustionSide(int scoreSign) {
        this.scoreSign = scoreSign;
    }

    /**
     * @return sign to apply to the bounded numeric exhaustion score
     * @since 0.23.1
     */
    public int scoreSign() {
        return scoreSign;
    }
}

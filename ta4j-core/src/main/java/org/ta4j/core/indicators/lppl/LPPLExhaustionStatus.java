/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

/**
 * Status of a Log-Periodic Power Law (LPPL) exhaustion calculation or fit
 * attempt.
 *
 * @since 0.23.1
 */
public enum LPPLExhaustionStatus {

    /**
     * The fit or aggregate result is usable. A valid aggregate can retain
     * structurally qualified evidence without having a near-term actionable side.
     */
    VALID,

    /**
     * The requested index does not yet have enough historical bars.
     */
    INSUFFICIENT_DATA,

    /**
     * The input window contains non-positive, NaN, or otherwise unusable prices.
     */
    INVALID_INPUT,

    /**
     * Least-squares calibration did not converge within the configured budget.
     */
    OPTIMIZER_FAILED,

    /**
     * Calibration converged, but no fit passed the configured structural LPPL
     * qualification filters.
     */
    NO_VALID_FIT
}

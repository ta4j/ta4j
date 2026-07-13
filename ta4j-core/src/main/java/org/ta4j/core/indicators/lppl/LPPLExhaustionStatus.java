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
     * The fit or aggregate signal is usable.
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
     * Calibration converged, but no fit passed the configured LPPL filters.
     */
    NO_VALID_FIT
}

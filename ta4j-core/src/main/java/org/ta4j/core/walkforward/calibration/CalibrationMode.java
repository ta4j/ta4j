/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.calibration;

/**
 * Calibration modes for tuner evaluation.
 *
 * @since 0.22.4
 */
public enum CalibrationMode {

    /** No probability calibration in tuning. */
    NONE,

    /** Fit and apply Platt calibration only. */
    PLATT,

    /** Fit Platt and evaluate isotonic as a gated challenger. */
    PLATT_WITH_ISOTONIC_CHALLENGER
}

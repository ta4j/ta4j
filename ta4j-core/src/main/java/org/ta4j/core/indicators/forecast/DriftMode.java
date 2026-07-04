/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

/**
 * Drift assumption used by return forecast state.
 *
 * @since 0.22.9
 */
public enum DriftMode {

    /** Simulated paths use zero expected return per step. */
    ZERO,

    /** Simulated paths use the rolling mean return estimate as drift. */
    ROLLING_MEAN
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

/**
 * Outcome of one causal LPPL calibration and evaluation.
 *
 * @since 0.23.1
 */
public enum LPPLFitStatus {

    /** The configured trailing window is not available. */
    INSUFFICIENT_DATA,

    /** The source window contains a missing, non-finite, or non-positive price. */
    INVALID_INPUT,

    /** No finite model was produced within the configured optimizer budget. */
    OPTIMIZER_FAILED,

    /** Finite model parameters and evaluation diagnostics are available. */
    VALID
}

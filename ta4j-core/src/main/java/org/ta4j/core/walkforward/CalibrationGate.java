/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

/**
 * Gating thresholds for selecting isotonic calibration over Platt calibration.
 *
 * @param minimumSamples          minimum samples required to evaluate isotonic
 * @param minimumEceImprovement   minimum ECE improvement required to switch to
 *                                isotonic
 * @param maximumVarianceIncrease maximum allowed fold-variance increase versus
 *                                Platt
 * @since 0.22.4
 */
public record CalibrationGate(int minimumSamples, double minimumEceImprovement, double maximumVarianceIncrease) {

    /**
     * Creates a validated calibration gate.
     */
    public CalibrationGate {
        if (minimumSamples <= 0) {
            throw new IllegalArgumentException("minimumSamples must be > 0");
        }
        if (minimumEceImprovement < 0.0) {
            throw new IllegalArgumentException("minimumEceImprovement must be >= 0.0");
        }
        if (maximumVarianceIncrease < 0.0) {
            throw new IllegalArgumentException("maximumVarianceIncrease must be >= 0.0");
        }
    }

    /**
     * @return default gate values
     * @since 0.22.4
     */
    public static CalibrationGate defaultGate() {
        return new CalibrationGate(150, 0.005, 0.01);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

/**
 * Calibration selection summary for one candidate evaluation.
 *
 * @param selected    selected calibrator id ({@code none}, {@code platt},
 *                    {@code isotonic})
 * @param sampleCount number of samples used to fit/evaluate calibration
 * @param rawEce      raw expected calibration error
 * @param plattEce    Platt-calibrated expected calibration error
 * @param isotonicEce isotonic-calibrated expected calibration error
 * @param reason      selection rationale
 * @since 0.22.4
 */
public record CalibrationSelection(String selected, int sampleCount, double rawEce, double plattEce, double isotonicEce,
        String reason) {

    /**
     * @return selection for no-calibration mode
     * @since 0.22.4
     */
    public static CalibrationSelection none() {
        return new CalibrationSelection("none", 0, Double.NaN, Double.NaN, Double.NaN, "calibration disabled");
    }
}

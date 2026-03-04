/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class CalibrationComponentsTest {

    @Test
    void calibrationGateValidatesAndExposesDefaults() {
        CalibrationGate gate = CalibrationGate.defaultGate();

        assertThat(gate.minimumSamples()).isEqualTo(150);
        assertThat(gate.minimumEceImprovement()).isEqualTo(0.005);
        assertThat(gate.maximumVarianceIncrease()).isEqualTo(0.01);

        assertThrows(IllegalArgumentException.class, () -> new CalibrationGate(0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CalibrationGate(10, -0.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CalibrationGate(10, 0.1, -0.1));
    }

    @Test
    void calibrationSelectionNoneBuildsDisabledSummary() {
        CalibrationSelection selection = CalibrationSelection.none();

        assertThat(selection.selected()).isEqualTo("none");
        assertThat(selection.sampleCount()).isZero();
        assertThat(selection.reason()).contains("disabled");
        assertThat(selection.rawEce()).isNaN();
    }

    @Test
    void plattCalibratorKeepsOutputWithinBoundsAndRespondsToFit() {
        PlattCalibrator calibrator = new PlattCalibrator();
        calibrator.fit(List.of(0.1, 0.2, 0.8, 0.9), List.of(0.0, 0.0, 1.0, 1.0));

        double low = calibrator.calibrate(0.2);
        double high = calibrator.calibrate(0.8);
        assertThat(low).isBetween(0.0, 1.0);
        assertThat(high).isBetween(0.0, 1.0);
        assertThat(high).isGreaterThan(low);

        calibrator.fit(List.of(0.2, 0.3), List.of(1.0));
        double invalidFitOutput = calibrator.calibrate(Double.NaN);
        assertThat(invalidFitOutput).isBetween(0.0, 1.0);
    }

    @Test
    void isotonicCalibratorProducesMonotonicCalibrationCurve() {
        IsotonicCalibrator calibrator = new IsotonicCalibrator();
        calibrator.fit(List.of(0.1, 0.2, 0.4, 0.5, 0.6, 0.9), List.of(0.0, 0.1, 0.4, 0.45, 0.8, 1.0));

        double p1 = calibrator.calibrate(0.15);
        double p2 = calibrator.calibrate(0.55);
        double p3 = calibrator.calibrate(0.85);

        assertThat(p1).isBetween(0.0, 1.0);
        assertThat(p2).isBetween(0.0, 1.0);
        assertThat(p3).isBetween(0.0, 1.0);
        assertThat(p2).isGreaterThanOrEqualTo(p1);
        assertThat(p3).isGreaterThanOrEqualTo(p2);

        calibrator.fit(List.of(), List.of());
        assertThat(calibrator.calibrate(Double.NaN)).isBetween(0.0, 1.0);
    }

    @Test
    void calibrationModeIncludesSupportedPolicies() {
        assertThat(CalibrationMode.values()).containsExactly(CalibrationMode.NONE, CalibrationMode.PLATT,
                CalibrationMode.PLATT_WITH_ISOTONIC_CHALLENGER);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class WalkForwardCalibrationComponentsTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void defaultGateExposesExpectedThresholds() {
        WalkForwardTuner.CalibrationGate gate = WalkForwardTuner.CalibrationGate.defaultGate();

        assertThat(gate.minimumSamples()).isEqualTo(150);
        assertThat(gate.minimumEceImprovement().doubleValue()).isEqualTo(0.005);
        assertThat(gate.maximumVarianceIncrease().doubleValue()).isEqualTo(0.01);
    }

    @Test
    void gateValidatesBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalkForwardTuner.CalibrationGate(0, NUM_FACTORY.zero(), NUM_FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new WalkForwardTuner.CalibrationGate(10, NUM_FACTORY.numOf(-0.1), NUM_FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new WalkForwardTuner.CalibrationGate(10, NUM_FACTORY.numOf(0.1), NUM_FACTORY.numOf(-0.1)));
    }

    @Test
    void noneSelectionIsStable() {
        WalkForwardTuner.CalibrationSelection selection = WalkForwardTuner.CalibrationSelection.none();

        assertThat(selection.selected()).isEqualTo("none");
        assertThat(selection.sampleCount()).isZero();
        assertThat(selection.rawEce().isNaN()).isTrue();
        assertThat(selection.reason()).isEqualTo("calibration disabled");
    }

    @Test
    void calibrationModesExposeExpectedSet() {
        assertThat(WalkForwardTuner.CalibrationMode.values()).containsExactly(WalkForwardTuner.CalibrationMode.NONE,
                WalkForwardTuner.CalibrationMode.PLATT,
                WalkForwardTuner.CalibrationMode.PLATT_WITH_ISOTONIC_CHALLENGER);
    }
}

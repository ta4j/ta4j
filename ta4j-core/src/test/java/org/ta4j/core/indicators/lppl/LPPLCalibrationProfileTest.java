/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LPPLCalibrationProfileTest {

    @Test
    void groupedTuningPreservesUnchangedDefaults() {
        LPPLCalibrationProfile defaults = LPPLCalibrationProfile.defaults();
        LPPLCalibrationProfile profile = defaults.withWindows(80, 40, 80)
                .withExponentSearch(0.2, 0.8, 4)
                .withFrequencySearch(7.0, 11.0, 6)
                .withCriticalTimeSearch(2, 40, 2)
                .withActionableCriticalTimeRange(8, 24)
                .withOptimizerSettings(90, 0.8);

        assertThat(profile.windows()).containsExactly(40, 80);
        assertThat(profile.minM()).isEqualTo(0.2);
        assertThat(profile.maxM()).isEqualTo(0.8);
        assertThat(profile.mSteps()).isEqualTo(4);
        assertThat(profile.minOmega()).isEqualTo(7.0);
        assertThat(profile.maxOmega()).isEqualTo(11.0);
        assertThat(profile.omegaSteps()).isEqualTo(6);
        assertThat(profile.minCriticalOffset()).isEqualTo(2);
        assertThat(profile.maxCriticalOffset()).isEqualTo(40);
        assertThat(profile.criticalOffsetStep()).isEqualTo(2);
        assertThat(profile.activeMinCriticalOffset()).isEqualTo(8);
        assertThat(profile.activeMaxCriticalOffset()).isEqualTo(24);
        assertThat(profile.maxEvaluations()).isEqualTo(90);
        assertThat(profile.minRSquared()).isEqualTo(0.8);
        assertThat(defaults.windows()).containsExactly(200, 300, 400, 500);
    }

    @Test
    void windowsHaveValueSemanticsAndDefensiveCopies() {
        int[] windows = { 80, 40, 80 };
        LPPLCalibrationProfile profile = LPPLCalibrationProfile.defaults().withWindows(windows);
        windows[0] = 5;

        LPPLCalibrationProfile equivalent = LPPLCalibrationProfile.defaults().withWindows(40, 80);
        assertThat(profile.windows()).containsExactly(40, 80);
        assertThat(profile).isEqualTo(equivalent).hasSameHashCodeAs(equivalent);
        assertThat(profile.toString()).contains("windows=[40, 80]");

        int[] copy = profile.windows();
        copy[0] = 5;
        assertThat(profile.windows()).containsExactly(40, 80);
    }

    @Test
    void narrowingCriticalTimeSearchKeepsTheProfileUsable() {
        LPPLCalibrationProfile overlapping = LPPLCalibrationProfile.defaults().withCriticalTimeSearch(5, 20, 1);
        LPPLCalibrationProfile disjoint = LPPLCalibrationProfile.defaults().withCriticalTimeSearch(1, 5, 1);

        assertThat(overlapping.activeMinCriticalOffset()).isEqualTo(10);
        assertThat(overlapping.activeMaxCriticalOffset()).isEqualTo(20);
        assertThat(disjoint.activeMinCriticalOffset()).isEqualTo(1);
        assertThat(disjoint.activeMaxCriticalOffset()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidGroupedSettings() {
        LPPLCalibrationProfile defaults = LPPLCalibrationProfile.defaults();

        assertThatThrownBy(() -> defaults.withWindows(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withExponentSearch(0.9, 0.1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withFrequencySearch(8.0, 7.0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withCriticalTimeSearch(1, 30, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withActionableCriticalTimeRange(0, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withOptimizerSettings(0, 0.6)).isInstanceOf(IllegalArgumentException.class);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LPPLCalibrationProfileTest {

    @Test
    void defaultsProvideOneCausalFitWindow() {
        LPPLCalibrationProfile profile = LPPLCalibrationProfile.defaults();

        assertThat(profile.window()).isEqualTo(500);
        assertThat(profile.minCriticalOffset()).isEqualTo(1);
        assertThat(profile.maxCriticalOffset()).isEqualTo(60);
        assertThat(profile.minRSquared()).isEqualTo(0.75);
    }

    @Test
    void groupedModifiersAreImmutableAndValueBased() {
        LPPLCalibrationProfile defaults = LPPLCalibrationProfile.defaults();
        LPPLCalibrationProfile customized = defaults.withWindow(250)
                .withExponentSearch(0.2, 0.8, 4)
                .withFrequencySearch(7.0, 12.0, 6)
                .withCriticalTimeSearch(2, 90, 3)
                .withOptimizerSettings(200, 0.8);
        LPPLCalibrationProfile same = LPPLCalibrationProfile.defaults()
                .withWindow(250)
                .withExponentSearch(0.2, 0.8, 4)
                .withFrequencySearch(7.0, 12.0, 6)
                .withCriticalTimeSearch(2, 90, 3)
                .withOptimizerSettings(200, 0.8);

        assertThat(defaults.window()).isEqualTo(500);
        assertThat(customized).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(customized.toString()).contains("window=250", "minCriticalOffset=2", "minRSquared=0.8");
    }

    @Test
    void rejectsInvalidSearchSettings() {
        assertThrows(IllegalArgumentException.class, () -> LPPLCalibrationProfile.defaults().withWindow(4));
        assertThrows(IllegalArgumentException.class,
                () -> LPPLCalibrationProfile.defaults().withCriticalTimeSearch(0, 30, 5));
        assertThrows(IllegalArgumentException.class,
                () -> LPPLCalibrationProfile.defaults().withOptimizerSettings(10, 1.1));
    }
}

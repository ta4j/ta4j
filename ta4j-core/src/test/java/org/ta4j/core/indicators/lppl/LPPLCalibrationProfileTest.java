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

    @Test
    void rejectsSearchSettingsBeyondTheFitWorkBudget() {
        LPPLCalibrationProfile defaults = LPPLCalibrationProfile.defaults();

        assertThrows(IllegalArgumentException.class,
                () -> defaults.withExponentSearch(0.1, 0.9, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withCriticalTimeSearch(1, Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> defaults.withExponentSearch(0.1, 0.9, 1_000)
                .withFrequencySearch(6.0, 13.0, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withOptimizerSettings(LPPLCalibrationProfile.MAX_EVALUATIONS + 1, 0.75));
        assertThrows(IllegalArgumentException.class, () -> defaults.withWindow(Integer.MAX_VALUE));
    }

    @Test
    void acceptsResearchSizedSearchInsideTheBudget() {
        LPPLCalibrationProfile profile = LPPLCalibrationProfile.defaults()
                .withWindow(2_000)
                .withExponentSearch(0.1, 0.9, 20)
                .withFrequencySearch(6.0, 13.0, 40)
                .withOptimizerSettings(1_000, 0.75);

        assertThat(profile.window()).isEqualTo(2_000);
    }
}

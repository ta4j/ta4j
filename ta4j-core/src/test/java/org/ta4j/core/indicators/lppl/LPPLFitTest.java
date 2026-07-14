/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LPPLFitTest {

    @Test
    void qualifiedFitExposesCausalEvaluationDiagnostics() {
        LPPLCalibrationProfile profile = LPPLTestFixtures.compactProfile();
        LPPLFit fit = new LPPLFit(profile.window(), LPPLFitStatus.VALID, 4.6, -0.03, 0.01, -0.006, 100.0, 0.5, 8.0, 0.2,
                0.05, 0.9, 20, 12, 4.8, 0.2, 0.4, 0.5);

        assertThat(fit.isConverged()).isTrue();
        assertThat(fit.isQualified(profile)).isTrue();
        assertThat(fit.fittedLogPriceAt(profile.window())).isFinite();
    }

    @Test
    void invalidOrOutOfProfileFitsAreNotQualified() {
        LPPLCalibrationProfile profile = LPPLTestFixtures.compactProfile();
        LPPLFit invalid = LPPLFit.invalid(profile.window(), LPPLFitStatus.OPTIMIZER_FAILED);
        LPPLFit weak = new LPPLFit(profile.window(), LPPLFitStatus.VALID, 4.6, -0.03, 0.01, -0.006, 100.0, 0.5, 8.0,
                0.2, 0.05, 0.2, 20, 12, 4.8, 0.2, 0.4, 0.5);

        assertThat(invalid.isQualified(profile)).isFalse();
        assertThat(weak.isQualified(profile)).isFalse();
        assertThat(invalid.fittedLogPriceAt(1)).isNaN();
    }

    @Test
    void validFitsRejectInconsistentNormalizedDiagnostics() {
        assertThrows(IllegalArgumentException.class, () -> new LPPLFit(80, LPPLFitStatus.VALID, 4.6, -0.03, 0.01,
                -0.006, 100.0, 0.5, 8.0, 0.2, 0.05, 0.9, 20, 12, 4.8, 0.2, 0.1, 2.0));
    }
}

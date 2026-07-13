/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LPPLFitTest {

    @Test
    void exposesConvergenceActionabilityAndSide() {
        LPPLFit fit = validFit(0.03);

        assertThat(fit.isConverged()).isTrue();
        assertThat(fit.isActionable(LPPLTestFixtures.compactProfile())).isTrue();
        assertThat(fit.side()).isEqualTo(LPPLExhaustionSide.CRASH_EXHAUSTION);
        assertThat(validFit(-0.03).side()).isEqualTo(LPPLExhaustionSide.BUBBLE_EXHAUSTION);
    }

    @Test
    void rejectsValidFitWithNonFiniteDiagnostics() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LPPLFit(LPPLTestFixtures.WINDOW, LPPLExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02,
                        LPPLTestFixtures.WINDOW + 20.0, 0.5, 8.0, Double.NaN, 0.1, 0.9, 20, 5));

        assertThat(exception).hasMessageContaining("valid fits");
    }

    @Test
    void rejectsValidFitBelowCalibrationMinimumWindow() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new LPPLFit(4,
                LPPLExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02, 24.0, 0.5, 8.0, 0.1, 0.1, 0.9, 20, 5));

        assertThat(exception).hasMessageContaining("at least five bars");
    }

    @Test
    void rejectsNegativeEvaluationCount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LPPLFit(LPPLTestFixtures.WINDOW, LPPLExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02,
                        LPPLTestFixtures.WINDOW + 20.0, 0.5, 8.0, 0.1, 0.1, 0.9, 20, -1));

        assertThat(exception).hasMessageContaining("evaluations must be non-negative");
    }

    @Test
    void zeroQualityFitIsNotActionableAtAZeroThreshold() {
        LPPLFit fit = new LPPLFit(LPPLTestFixtures.WINDOW, LPPLExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02,
                LPPLTestFixtures.WINDOW + 20.0, 0.5, 8.0, 0.1, 0.1, 0.0, 20, 5);
        LPPLCalibrationProfile profile = LPPLTestFixtures.compactProfile().withOptimizerSettings(80, 0.0);

        assertThat(fit.isActionable(profile)).isFalse();
    }

    static LPPLFit validFit(double b) {
        return new LPPLFit(LPPLTestFixtures.WINDOW, LPPLExhaustionStatus.VALID, 1.0, b, 0.01, 0.02,
                LPPLTestFixtures.WINDOW + 20.0, 0.5, 8.0, 0.1, 0.1, 0.9, 20, 5);
    }
}

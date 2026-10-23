/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

class ShockPathQualificationTest {

    private static final ShockPathQualification MEASURED = ShockPathQualification.of(Backend.METAL, 1, "fixture",
            new ShockPathQualification.Coefficients(500_000_000L, 200_000L, 10d, 0.1d, 1L << 24));

    @AfterEach
    void reset() {
        System.clearProperty(ShockPathQualification.minStepsProperty(Backend.METAL));
    }

    @Test
    void shippedQualificationKeepsEveryBackendScalar() {
        for (Backend backend : new Backend[] { Backend.METAL, Backend.CUDA, Backend.OPENCL }) {
            for (String family : new String[] { "generic", "m5max" }) {
                assertThat(ShockPathQualification.QUALIFIED.predictedTotalNanos(backend, 1, family, 1L << 40, 0L, true))
                        .as("%s/%s", backend, family)
                        .isEqualTo(Long.MAX_VALUE);
            }
        }
    }

    @Test
    void minimumStepsOverrideNeverQualifiesAMissingRow() {
        System.setProperty(ShockPathQualification.minStepsProperty(Backend.METAL), "1");

        assertThat(ShockPathQualification.QUALIFIED.predictedTotalNanos(Backend.METAL, 1, "m5max", 1L << 30, 0L, true))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void measuredRowPredictsDistinctColdAndWarmTotals() {
        long steps = 1L << 24;
        long staged = 1_000L;

        long cold = MEASURED.predictedTotalNanos(Backend.METAL, 1, "fixture", steps, staged, false);
        long warm = MEASURED.predictedTotalNanos(Backend.METAL, 1, "fixture", steps, staged, true);

        assertThat(cold).isEqualTo(500_000_000L + steps * 10L + 100L);
        assertThat(warm).isEqualTo(200_000L + steps * 10L + 100L);
    }

    @Test
    void measuredRowStaysScalarBelowItsFloorOrForOtherVersionsAndFamilies() {
        assertThat(MEASURED.predictedTotalNanos(Backend.METAL, 1, "fixture", 1024L, 0L, true))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(MEASURED.predictedTotalNanos(Backend.METAL, 2, "fixture", 1L << 30, 0L, true))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(MEASURED.predictedTotalNanos(Backend.METAL, 1, "generic", 1L << 30, 0L, true))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void minimumStepsOverrideMovesTheCrossoverWithinAMeasuredRow() {
        System.setProperty(ShockPathQualification.minStepsProperty(Backend.METAL), "8");

        assertThat(MEASURED.predictedTotalNanos(Backend.METAL, 1, "fixture", 100L, 0L, true))
                .isEqualTo(200_000L + 1_000L);

        System.setProperty(ShockPathQualification.minStepsProperty(Backend.METAL), "-1");
        assertThat(MEASURED.minimumSteps(Backend.METAL, 1, "fixture")).isEqualTo(1L << 24);
    }
}

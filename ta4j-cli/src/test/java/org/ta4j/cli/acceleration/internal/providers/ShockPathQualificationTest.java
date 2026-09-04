/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

class ShockPathQualificationTest {

    @AfterEach
    void reset() {
        System.clearProperty(ShockPathQualification.minStepsProperty(Backend.METAL));
    }

    @Test
    void unknownFamilyPredictsUnboundedCost() {
        assertThat(ShockPathQualification.predictedTotalNanos(Backend.METAL, 1, "generic", 1L << 30, 0L, false))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void unknownVersionPredictsUnboundedCost() {
        assertThat(ShockPathQualification.predictedTotalNanos(Backend.METAL, 999, "m5max", 1L << 30, 0L, false))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void belowFloorPredictsUnboundedCost() {
        assertThat(ShockPathQualification.predictedTotalNanos(Backend.METAL, 1, "m5max", 1024L, 0L, false))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void qualifiedPredictionCarriesColdAndWarmBases() {
        long steps = 1L << 24;
        long staged = 1_000L;

        long cold = ShockPathQualification.predictedTotalNanos(Backend.METAL, 1, "m5max", steps, staged, false);
        long warm = ShockPathQualification.predictedTotalNanos(Backend.METAL, 1, "m5max", steps, staged, true);

        assertThat(cold).isEqualTo(500_000_000L + (long) (steps * 37.5d) + (long) (staged * 0.1d));
        assertThat(warm).isEqualTo(200_000L + (long) (steps * 37.5d) + (long) (staged * 0.1d));
        assertThat(warm).isLessThan(cold);
    }

    @Test
    void minimumStepsOverrideMovesCrossover() {
        assertThat(ShockPathQualification.predictedTotalNanos(Backend.METAL, 1, "m5max", 100L, 0L, true))
                .isEqualTo(Long.MAX_VALUE);

        System.setProperty(ShockPathQualification.minStepsProperty(Backend.METAL), "8");

        assertThat(ShockPathQualification.predictedTotalNanos(Backend.METAL, 1, "m5max", 100L, 0L, true))
                .isEqualTo(200_000L + (long) (100L * 37.5d));
    }

    @Test
    void negativeOverrideFallsBackToQualifiedFloor() {
        System.setProperty(ShockPathQualification.minStepsProperty(Backend.METAL), "-1");

        assertThat(ShockPathQualification.minimumSteps(Backend.METAL, 1, "m5max")).isEqualTo(16_777_216L);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WyckoffPhaseTest {

    /**
     * Verifies that provide convenience mutators.
     */
    @Test
    public void shouldProvideConvenienceMutators() {
        WyckoffPhase base = new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_A, 0.4, 5);

        WyckoffPhase updatedConfidence = base.withConfidence(0.65);
        assertThat(updatedConfidence.confidence()).isEqualTo(0.65);
        assertThat(updatedConfidence.latestEventIndex()).isEqualTo(5);

        WyckoffPhase updatedEvent = base.withLatestEventIndex(12);
        assertThat(updatedEvent.latestEventIndex()).isEqualTo(12);
        assertThat(updatedEvent.confidence()).isEqualTo(0.4);
    }

    /**
     * Verifies that reject invalid confidence values.
     */
    @Test
    public void shouldRejectInvalidConfidenceValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_A, -0.01, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_A, 1.01, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_A, Double.NaN, 3));
        assertThrows(IllegalArgumentException.class, () -> new WyckoffPhase(WyckoffCycleType.ACCUMULATION,
                WyckoffPhaseType.PHASE_A, Double.POSITIVE_INFINITY, 3));
    }

    /**
     * Verifies that reject null cycle or phase.
     */
    @Test
    public void shouldRejectNullCycleOrPhase() {
        assertThrows(NullPointerException.class, () -> new WyckoffPhase(null, WyckoffPhaseType.PHASE_A, 0.4, 3));
        assertThrows(NullPointerException.class, () -> new WyckoffPhase(WyckoffCycleType.ACCUMULATION, null, 0.4, 3));
    }
}

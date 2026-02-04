/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class WyckoffPhaseTest {

    @Test
    public void shouldProvideConvenienceMutators() {
        var base = new WyckoffPhase(WyckoffCycleType.ACCUMULATION, WyckoffPhaseType.PHASE_A, 0.4, 5);

        var updatedConfidence = base.withConfidence(0.65);
        assertThat(updatedConfidence.confidence()).isEqualTo(0.65);
        assertThat(updatedConfidence.latestEventIndex()).isEqualTo(5);

        var updatedEvent = base.withLatestEventIndex(12);
        assertThat(updatedEvent.latestEventIndex()).isEqualTo(12);
        assertThat(updatedEvent.confidence()).isEqualTo(0.4);
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScenarioTypeTest {

    @Test
    void impulseTypeProperties() {
        assertThat(ScenarioType.IMPULSE.isImpulse()).isTrue();
        assertThat(ScenarioType.IMPULSE.isCorrective()).isFalse();
        assertThat(ScenarioType.IMPULSE.expectedWaveCount()).isEqualTo(5);
    }

    @Test
    void zigzagTypeProperties() {
        assertThat(ScenarioType.CORRECTIVE_ZIGZAG.isImpulse()).isFalse();
        assertThat(ScenarioType.CORRECTIVE_ZIGZAG.isCorrective()).isTrue();
        assertThat(ScenarioType.CORRECTIVE_ZIGZAG.expectedWaveCount()).isEqualTo(3);
    }

    @Test
    void flatTypeProperties() {
        assertThat(ScenarioType.CORRECTIVE_FLAT.isImpulse()).isFalse();
        assertThat(ScenarioType.CORRECTIVE_FLAT.isCorrective()).isTrue();
        assertThat(ScenarioType.CORRECTIVE_FLAT.expectedWaveCount()).isEqualTo(3);
    }

    @Test
    void triangleTypeProperties() {
        assertThat(ScenarioType.CORRECTIVE_TRIANGLE.isImpulse()).isFalse();
        assertThat(ScenarioType.CORRECTIVE_TRIANGLE.isCorrective()).isTrue();
        assertThat(ScenarioType.CORRECTIVE_TRIANGLE.expectedWaveCount()).isEqualTo(5);
    }

    @Test
    void unknownTypeProperties() {
        assertThat(ScenarioType.UNKNOWN.isImpulse()).isFalse();
        assertThat(ScenarioType.UNKNOWN.isCorrective()).isFalse();
        assertThat(ScenarioType.UNKNOWN.expectedWaveCount()).isZero();
    }
}

/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ElliottInvalidationIndicatorTest {

    @Test
    void shouldRemainFalseForValidImpulse() {
        var series = new MockBarSeriesBuilder().build();
        var swings = ElliottPhaseIndicatorTest.impulseAndCorrectionSwings(series.numFactory());
        var swingIndicator = new StubSwingIndicator(series, swings);
        var phaseIndicator = new ElliottPhaseIndicator(swingIndicator);
        var invalidation = new ElliottInvalidationIndicator(phaseIndicator);

        for (int i = 0; i <= 5; i++) {
            phaseIndicator.getValue(i);
            assertThat(invalidation.getValue(i)).isFalse();
        }
    }

    @Test
    void shouldDetectWaveFourOverlap() {
        var series = new MockBarSeriesBuilder().build();
        var factory = series.numFactory();

        var wave1 = new ElliottSwing(0, 3, factory.numOf(100), factory.numOf(110), ElliottDegree.MINOR);
        var wave2 = new ElliottSwing(3, 5, factory.numOf(110), factory.numOf(104), ElliottDegree.MINOR);
        var wave3 = new ElliottSwing(5, 9, factory.numOf(104), factory.numOf(130), ElliottDegree.MINOR);
        var wave4 = new ElliottSwing(9, 11, factory.numOf(130), factory.numOf(109.6), ElliottDegree.MINOR);

        var swings = new ArrayList<List<ElliottSwing>>();
        swings.add(List.<ElliottSwing>of());
        swings.add(List.of(wave1));
        swings.add(List.of(wave1, wave2));
        swings.add(List.of(wave1, wave2, wave3));
        swings.add(List.of(wave1, wave2, wave3, wave4));

        var swingIndicator = new StubSwingIndicator(series, swings);
        var phaseIndicator = new ElliottPhaseIndicator(swingIndicator);
        var invalidation = new ElliottInvalidationIndicator(phaseIndicator);

        phaseIndicator.getValue(3);
        assertThat(invalidation.getValue(4)).isTrue();
    }
}

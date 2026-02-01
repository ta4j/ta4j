/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class ElliottPhaseIndicatorTest {

    @Test
    void shouldProgressThroughImpulsePhases() {
        var series = new MockBarSeriesBuilder().build();
        var swings = impulseAndCorrectionSwings(series.numFactory());
        var swingIndicator = new StubSwingIndicator(series, swings);
        var indicator = new ElliottPhaseIndicator(swingIndicator);

        assertThat(indicator.getValue(0)).isEqualTo(ElliottPhase.NONE);
        assertThat(indicator.getValue(1)).isEqualTo(ElliottPhase.WAVE1);
        assertThat(indicator.getValue(2)).isEqualTo(ElliottPhase.WAVE2);
        assertThat(indicator.getValue(3)).isEqualTo(ElliottPhase.WAVE3);
        assertThat(indicator.getValue(4)).isEqualTo(ElliottPhase.WAVE4);
        assertThat(indicator.getValue(5)).isEqualTo(ElliottPhase.WAVE5);
        assertThat(indicator.isImpulseConfirmed(5)).isTrue();
        assertThat(indicator.isCorrectiveConfirmed(5)).isFalse();
    }

    @Test
    void shouldIdentifyCorrectiveStructure() {
        var series = new MockBarSeriesBuilder().build();
        var swings = impulseAndCorrectionSwings(series.numFactory());
        var swingIndicator = new StubSwingIndicator(series, swings);
        var indicator = new ElliottPhaseIndicator(swingIndicator);

        // Prime cache up to wave five
        indicator.getValue(5);

        assertThat(indicator.getValue(6)).isEqualTo(ElliottPhase.CORRECTIVE_A);
        assertThat(indicator.getValue(7)).isEqualTo(ElliottPhase.CORRECTIVE_B);
        assertThat(indicator.getValue(8)).isEqualTo(ElliottPhase.CORRECTIVE_C);
        assertThat(indicator.isCorrectiveConfirmed(8)).isTrue();

        assertThat(indicator.impulseSwings(8)).hasSize(5);
        assertThat(indicator.correctiveSwings(8)).hasSize(3);
    }

    @Test
    void shouldReturnNoneWhenSwingPricesContainNaN() {
        var series = new MockBarSeriesBuilder().build();
        var factory = series.numFactory();
        var swings = new ArrayList<List<ElliottSwing>>();
        swings.add(List.of());
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(100), NaN, ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(100), NaN, ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(110), factory.numOf(103), ElliottDegree.MINOR)));

        var swingIndicator = new StubSwingIndicator(series, swings);
        var indicator = new ElliottPhaseIndicator(swingIndicator);

        assertThat(indicator.getValue(1)).isEqualTo(ElliottPhase.NONE);
        assertThat(indicator.isImpulseConfirmed(2)).isFalse();
    }

    @Test
    void shouldTrackBearishImpulse() {
        var series = new MockBarSeriesBuilder().build();
        var factory = series.numFactory();

        var wave1 = new ElliottSwing(0, 2, factory.numOf(100), factory.numOf(90), ElliottDegree.MINOR);
        var wave2 = new ElliottSwing(2, 4, factory.numOf(90), factory.numOf(95), ElliottDegree.MINOR);
        var wave3 = new ElliottSwing(4, 7, factory.numOf(95), factory.numOf(74), ElliottDegree.MINOR);
        var wave4 = new ElliottSwing(7, 9, factory.numOf(74), factory.numOf(81), ElliottDegree.MINOR);
        var wave5 = new ElliottSwing(9, 12, factory.numOf(81), factory.numOf(65), ElliottDegree.MINOR);

        var swings = List.of(List.<ElliottSwing>of(), List.of(wave1), List.of(wave1, wave2),
                List.of(wave1, wave2, wave3), List.of(wave1, wave2, wave3, wave4),
                List.of(wave1, wave2, wave3, wave4, wave5));

        var swingIndicator = new StubSwingIndicator(series, swings);
        var indicator = new ElliottPhaseIndicator(swingIndicator);

        assertThat(indicator.getValue(5)).isEqualTo(ElliottPhase.WAVE5);
        assertThat(indicator.isImpulseConfirmed(5)).isTrue();
    }

    @Test
    void shouldResetAfterCompletedCorrection() {
        var series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14).build();
        var factory = series.numFactory();

        var swings = impulseAndCorrectionSwings(factory);
        var completed = swings.get(swings.size() - 1);

        var wave1 = new ElliottSwing(24, 27, factory.numOf(108), factory.numOf(118), ElliottDegree.MINOR);
        var wave2 = new ElliottSwing(27, 29, factory.numOf(118), factory.numOf(112), ElliottDegree.MINOR);
        var wave3 = new ElliottSwing(29, 33, factory.numOf(112), factory.numOf(130), ElliottDegree.MINOR);

        var next = new ArrayList<List<ElliottSwing>>();
        next.addAll(swings);
        next.add(List.copyOf(concat(completed, List.of(wave1))));
        next.add(List.copyOf(concat(completed, List.of(wave1, wave2))));
        next.add(List.copyOf(concat(completed, List.of(wave1, wave2, wave3))));

        var swingIndicator = new StubSwingIndicator(series, next);
        var indicator = new ElliottPhaseIndicator(swingIndicator);

        assertThat(indicator.getValue(8)).isEqualTo(ElliottPhase.CORRECTIVE_C);
        assertThat(indicator.getValue(9)).isEqualTo(ElliottPhase.WAVE1);
        assertThat(indicator.getValue(10)).isEqualTo(ElliottPhase.WAVE2);
        assertThat(indicator.getValue(11)).isEqualTo(ElliottPhase.WAVE3);
    }

    static List<List<ElliottSwing>> impulseAndCorrectionSwings(final NumFactory factory) {
        var wave1 = new ElliottSwing(0, 3, factory.numOf(100), factory.numOf(110), ElliottDegree.MINOR);
        var wave2 = new ElliottSwing(3, 5, factory.numOf(110), factory.numOf(104), ElliottDegree.MINOR);
        var wave3 = new ElliottSwing(5, 9, factory.numOf(104), factory.numOf(120), ElliottDegree.MINOR);
        var wave4 = new ElliottSwing(9, 11, factory.numOf(120), factory.numOf(114), ElliottDegree.MINOR);
        var wave5 = new ElliottSwing(11, 15, factory.numOf(114), factory.numOf(125), ElliottDegree.MINOR);
        var waveA = new ElliottSwing(15, 18, factory.numOf(125), factory.numOf(113), ElliottDegree.MINOR);
        var waveB = new ElliottSwing(18, 20, factory.numOf(113), factory.numOf(121), ElliottDegree.MINOR);
        var waveC = new ElliottSwing(20, 24, factory.numOf(121), factory.numOf(108), ElliottDegree.MINOR);

        var swings = new ArrayList<List<ElliottSwing>>();
        swings.add(List.<ElliottSwing>of());
        swings.add(List.of(wave1));
        swings.add(List.of(wave1, wave2));
        swings.add(List.of(wave1, wave2, wave3));
        swings.add(List.of(wave1, wave2, wave3, wave4));
        swings.add(List.of(wave1, wave2, wave3, wave4, wave5));
        swings.add(List.of(wave1, wave2, wave3, wave4, wave5, waveA));
        swings.add(List.of(wave1, wave2, wave3, wave4, wave5, waveA, waveB));
        swings.add(List.of(wave1, wave2, wave3, wave4, wave5, waveA, waveB, waveC));
        return swings;
    }

    private static List<ElliottSwing> concat(final List<ElliottSwing> left, final List<ElliottSwing> right) {
        var merged = new ArrayList<ElliottSwing>(left.size() + right.size());
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }
}

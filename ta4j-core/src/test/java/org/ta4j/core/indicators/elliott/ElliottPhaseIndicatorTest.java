/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
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

    private static final class StubSwingIndicator extends ElliottSwingIndicator {

        private final List<List<ElliottSwing>> swingsByIndex;

        private StubSwingIndicator(final BarSeries series, final List<List<ElliottSwing>> swingsByIndex) {
            super(series, 1, ElliottDegree.MINOR);
            this.swingsByIndex = swingsByIndex;
        }

        @Override
        protected List<ElliottSwing> calculate(final int index) {
            if (index < swingsByIndex.size()) {
                return swingsByIndex.get(index);
            }
            return swingsByIndex.get(swingsByIndex.size() - 1);
        }
    }
}

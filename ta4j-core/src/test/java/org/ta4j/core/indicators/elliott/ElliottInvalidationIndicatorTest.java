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

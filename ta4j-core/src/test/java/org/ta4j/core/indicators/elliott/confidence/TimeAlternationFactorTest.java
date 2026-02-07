/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottConfidenceScorer;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class TimeAlternationFactorTest {

    @Test
    void exposesWaveDurationDiagnostics() {
        BarSeries series = new MockBarSeriesBuilder().withName("AltTest").build();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 4, factory.hundred(), factory.numOf(150), degree),
                new ElliottSwing(4, 6, factory.numOf(150), factory.numOf(130), degree),
                new ElliottSwing(6, 12, factory.numOf(130), factory.numOf(200), degree),
                new ElliottSwing(12, 20, factory.numOf(200), factory.numOf(170), degree));

        ElliottConfidenceScorer scorer = new ElliottConfidenceScorer(series.numFactory());
        TimeAlternationFactor factor = new TimeAlternationFactor(scorer);
        ElliottConfidenceContext context = new ElliottConfidenceContext(swings, ElliottPhase.WAVE4, null,
                new ElliottFibonacciValidator(series.numFactory()), series.numFactory());

        ConfidenceFactorResult result = factor.score(context);

        assertThat(result.diagnostics()).containsEntry("barsWave2", 2);
        assertThat(result.diagnostics()).containsEntry("barsWave4", 8);
        assertThat(result.diagnostics()).containsEntry("durationRatio", 4.0);
        assertThat(result.score().doubleValue()).isCloseTo(0.40, within(0.05));
    }

}

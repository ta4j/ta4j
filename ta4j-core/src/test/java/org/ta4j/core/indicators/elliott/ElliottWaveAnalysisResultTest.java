/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottWaveAnalysisResultTest {

    @Test
    void degreeAnalysis_shouldRejectNullBarDuration() {
        ElliottAnalysisResult analysis = analysisResult();
        assertThrows(NullPointerException.class,
                () -> new ElliottWaveAnalysisResult.DegreeAnalysis(ElliottDegree.PRIMARY, 0, 1, null, 0.8, analysis));
    }

    @Test
    void baseScenarioAssessment_shouldRejectInvalidScores() {
        ElliottScenario scenario = scenario();
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnalysisResult.BaseScenarioAssessment(scenario, Double.NaN, 0.8, 0.8, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnalysisResult.BaseScenarioAssessment(scenario, 0.8, -0.1, 0.8, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnalysisResult.BaseScenarioAssessment(scenario, 0.8, 0.8, 1.1, List.of()));
    }

    @Test
    void supportingScenarioMatch_shouldRejectInvalidScores() {
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnalysisResult.SupportingScenarioMatch(ElliottDegree.INTERMEDIATE, "supporting",
                        1.1, 0.8, 0.8, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnalysisResult.SupportingScenarioMatch(ElliottDegree.INTERMEDIATE, "supporting",
                        0.8, Double.NaN, 0.8, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnalysisResult.SupportingScenarioMatch(ElliottDegree.INTERMEDIATE, "supporting",
                        0.8, 0.8, -0.1, 0.8));
    }

    @Test
    void analysisFor_shouldReturnMatchingDegree() {
        ElliottWaveAnalysisResult.DegreeAnalysis primary = new ElliottWaveAnalysisResult.DegreeAnalysis(
                ElliottDegree.PRIMARY, 5, 200, Duration.ofDays(1), 0.9, analysisResult());
        ElliottWaveAnalysisResult result = new ElliottWaveAnalysisResult(ElliottDegree.PRIMARY, List.of(primary),
                List.of(), List.of());

        assertThat(result.analysisFor(ElliottDegree.PRIMARY)).contains(primary);
        assertThat(result.analysisFor(ElliottDegree.INTERMEDIATE)).isEmpty();
    }

    @Test
    void rankedBaseScenariosForSpan_shouldPreferCloserAnchorSpan() {
        ElliottScenario spanAligned = scenario("span-aligned", 10, 20, 0.72);
        ElliottScenario lateStart = scenario("late-start", 15, 20, 0.78);
        ElliottWaveAnalysisResult result = new ElliottWaveAnalysisResult(ElliottDegree.PRIMARY, List.of(),
                List.of(new ElliottWaveAnalysisResult.BaseScenarioAssessment(lateStart, 0.78, 0.70, 0.76, List.of()),
                        new ElliottWaveAnalysisResult.BaseScenarioAssessment(spanAligned, 0.72, 0.70, 0.74, List.of())),
                List.of());

        assertThat(result.rankedBaseScenariosForSpan(10, 20)).extracting(assessment -> assessment.scenario().id())
                .containsExactly("span-aligned", "late-start");
    }

    @Test
    void rankedBaseScenariosForSpan_shouldRejectDescendingWindow() {
        ElliottWaveAnalysisResult result = new ElliottWaveAnalysisResult(ElliottDegree.PRIMARY, List.of(), List.of(),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> result.rankedBaseScenariosForSpan(5, 4));
    }

    private static ElliottAnalysisResult analysisResult() {
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(scenario()), 0);
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, 0, List.of(), List.of(), scenarios, Map.of(), null,
                scenarios.trendBias());
    }

    private static ElliottScenario scenario() {
        BarSeries series = new MockBarSeriesBuilder().withName("analysis-result-test").build();
        NumFactory numFactory = series.numFactory();
        Num score = numFactory.numOf(0.8);
        ElliottConfidence confidence = new ElliottConfidence(score, score, score, score, score, score, "test");
        ElliottSwing swing = new ElliottSwing(0, 1, numFactory.hundred(), numFactory.numOf(110), ElliottDegree.PRIMARY);

        return ElliottScenario.builder()
                .id("scenario-1")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(List.of(swing))
                .confidence(confidence)
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(95))
                .type(ScenarioType.IMPULSE)
                .build();
    }

    private static ElliottScenario scenario(String id, int startIndex, int endIndex, double confidenceScore) {
        BarSeries series = new MockBarSeriesBuilder().withName("analysis-result-span-test").build();
        NumFactory numFactory = series.numFactory();
        Num score = numFactory.numOf(confidenceScore);
        ElliottConfidence confidence = new ElliottConfidence(score, score, score, score, score, score, "test");
        ElliottSwing swing = new ElliottSwing(startIndex, endIndex, numFactory.hundred(), numFactory.numOf(110),
                ElliottDegree.PRIMARY);

        return ElliottScenario.builder()
                .id(id)
                .currentPhase(ElliottPhase.WAVE5)
                .swings(List.of(swing))
                .confidence(confidence)
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(95))
                .type(ScenarioType.IMPULSE)
                .build();
    }
}

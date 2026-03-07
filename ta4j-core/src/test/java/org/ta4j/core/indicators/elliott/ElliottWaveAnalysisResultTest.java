/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
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

    @Test
    void rankedBaseScenariosForSpan_shouldFilterByScenarioTemplate() {
        ElliottScenario bullishImpulse = scenario("bullish-impulse", 10, 20, 0.72, ElliottPhase.WAVE5,
                ScenarioType.IMPULSE, 5, true);
        ElliottScenario bearishCorrective = scenario("bearish-corrective", 10, 20, 0.80, ElliottPhase.CORRECTIVE_C,
                ScenarioType.CORRECTIVE_ZIGZAG, 3, false);
        ElliottScenario wrongDirection = scenario("wrong-direction", 10, 20, 0.85, ElliottPhase.WAVE5,
                ScenarioType.IMPULSE, 5, false);
        ElliottWaveAnalysisResult result = new ElliottWaveAnalysisResult(ElliottDegree.PRIMARY, List.of(), List.of(
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(wrongDirection, 0.85, 0.70, 0.82, List.of()),
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(bearishCorrective, 0.80, 0.70, 0.78, List.of()),
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(bullishImpulse, 0.72, 0.70, 0.74, List.of())),
                List.of());

        assertThat(
                result.rankedBaseScenariosForSpan(10, 20, ScenarioType.IMPULSE, ElliottPhase.WAVE5, 5, Boolean.TRUE, 3))
                .extracting(assessment -> assessment.scenario().id())
                .containsExactly("bullish-impulse");
        assertThat(result.recommendedBaseScenarioForSpan(10, 20, null, ElliottPhase.CORRECTIVE_C, 3, Boolean.FALSE, 3))
                .map(assessment -> assessment.scenario().id())
                .contains("bearish-corrective");
    }

    @Test
    void rankedBaseScenariosForSpan_shouldRejectInvalidTemplateArguments() {
        ElliottWaveAnalysisResult result = new ElliottWaveAnalysisResult(ElliottDegree.PRIMARY, List.of(), List.of(),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> result.rankedBaseScenariosForSpan(5, 10,
                ScenarioType.IMPULSE, ElliottPhase.WAVE5, 0, Boolean.TRUE, 3));
        assertThrows(IllegalArgumentException.class, () -> result.rankedBaseScenariosForSpan(5, 10,
                ScenarioType.IMPULSE, ElliottPhase.WAVE5, 5, Boolean.TRUE, -1));
    }

    @Test
    void rankedBaseScenariosForWindow_shouldPreferDominantPivotScenario() {
        BarSeries series = anchoredWindowSeries();
        ElliottScenario nonDominantWaveOne = scenario("non-dominant-wave1", 0.90, ElliottPhase.WAVE2,
                ScenarioType.IMPULSE, true, swing(series, 0, 1, 100, 120), swing(series, 1, 3, 120, 112));
        ElliottScenario dominantWaveOne = scenario("dominant-wave1", 0.80, ElliottPhase.WAVE2, ScenarioType.IMPULSE,
                true, swing(series, 0, 2, 100, 130), swing(series, 2, 3, 130, 112));
        ElliottWaveAnalysisResult result = new ElliottWaveAnalysisResult(ElliottDegree.PRIMARY, List.of(), List.of(
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(nonDominantWaveOne, 0.90, 0.70, 0.88, List.of()),
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(dominantWaveOne, 0.80, 0.70, 0.82, List.of())),
                List.of());

        assertThat(result.rankedBaseScenariosForWindow(series, 0, 3, ScenarioType.IMPULSE, ElliottPhase.WAVE2, 2,
                Boolean.TRUE, 1)).extracting(assessment -> assessment.scenario().id())
                .containsExactly("dominant-wave1", "non-dominant-wave1");
        assertThat(result.recommendedBaseScenarioForWindow(series, 0, 3, ScenarioType.IMPULSE, ElliottPhase.WAVE2, 2,
                Boolean.TRUE, 1)).map(assessment -> assessment.scenario().id()).contains("dominant-wave1");
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
        return scenario(id, startIndex, endIndex, confidenceScore, ElliottPhase.WAVE5, ScenarioType.IMPULSE, 1, null);
    }

    private static ElliottScenario scenario(String id, int startIndex, int endIndex, double confidenceScore,
            ElliottPhase phase, ScenarioType type, int waveCount, Boolean bullishDirection) {
        BarSeries series = new MockBarSeriesBuilder().withName("analysis-result-span-test").build();
        NumFactory numFactory = series.numFactory();
        Num score = numFactory.numOf(confidenceScore);
        ElliottConfidence confidence = new ElliottConfidence(score, score, score, score, score, score, "test");
        ElliottSwing swing = new ElliottSwing(startIndex, endIndex, numFactory.hundred(), numFactory.numOf(110),
                ElliottDegree.PRIMARY);

        ElliottScenario.Builder builder = ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .swings(List.of(swing))
                .confidence(confidence)
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(95))
                .type(type)
                .startIndex(startIndex);
        if (waveCount > 1) {
            List<ElliottSwing> swings = new java.util.ArrayList<>();
            int step = Math.max(1, (endIndex - startIndex) / waveCount);
            int from = startIndex;
            boolean rising = bullishDirection == null ? true : bullishDirection.booleanValue();
            for (int index = 0; index < waveCount; index++) {
                int to = index == waveCount - 1 ? endIndex : Math.min(endIndex, from + step);
                Num fromPrice = rising ? numFactory.numOf(100 + (index * 10)) : numFactory.numOf(110 - (index * 5));
                Num toPrice = rising ? numFactory.numOf(110 + (index * 10)) : numFactory.numOf(100 - (index * 5));
                swings.add(new ElliottSwing(from, to, fromPrice, toPrice, ElliottDegree.PRIMARY));
                from = to;
                rising = !rising;
            }
            builder.swings(swings);
        }
        if (bullishDirection != null) {
            builder.bullishDirection(bullishDirection.booleanValue());
        }
        return builder.build();
    }

    private static ElliottScenario scenario(String id, double confidenceScore, ElliottPhase phase, ScenarioType type,
            boolean bullishDirection, ElliottSwing... swings) {
        BarSeries series = new MockBarSeriesBuilder().withName("analysis-result-window-test").build();
        NumFactory numFactory = series.numFactory();
        Num score = numFactory.numOf(confidenceScore);
        ElliottConfidence confidence = new ElliottConfidence(score, score, score, score, score, score, "test");
        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .swings(List.of(swings))
                .confidence(confidence)
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(95))
                .type(type)
                .startIndex(swings[0].fromIndex())
                .bullishDirection(bullishDirection)
                .build();
    }

    private static ElliottSwing swing(BarSeries series, int fromIndex, int toIndex, double fromPrice, double toPrice) {
        return new ElliottSwing(fromIndex, toIndex, series.numFactory().numOf(fromPrice),
                series.numFactory().numOf(toPrice), ElliottDegree.PRIMARY);
    }

    private static BarSeries anchoredWindowSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("anchored-window").build();
        addBar(series, "2024-01-01T00:00:00Z", 100, 101, 99, 100);
        addBar(series, "2024-01-02T00:00:00Z", 100, 120, 100, 118);
        addBar(series, "2024-01-03T00:00:00Z", 118, 130, 117, 128);
        addBar(series, "2024-01-04T00:00:00Z", 128, 129, 110, 112);
        return series;
    }

    private static void addBar(BarSeries series, String endTimeUtc, double open, double high, double low,
            double close) {
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse(endTimeUtc))
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(1.0)
                .amount(close)
                .trades(1)
                .build());
    }
}

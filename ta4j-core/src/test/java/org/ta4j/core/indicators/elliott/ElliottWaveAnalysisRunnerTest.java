/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectorResult;
import org.ta4j.core.indicators.elliott.swing.SwingFilter;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class ElliottWaveAnalysisRunnerTest {

    @Test
    void appliesSwingFiltersBeforeScenarioGeneration() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), degree),
                new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(110), degree),
                new ElliottSwing(4, 6, factory.numOf(110), factory.numOf(130), degree));

        SwingDetector detector = (s, index, deg) -> SwingDetectorResult.fromSwings(swings);
        SwingFilter filter = input -> input.subList(0, 2);
        ConfidenceModel model = (input, phase, channel,
                type) -> new ElliottConfidenceBreakdown(new ElliottConfidence(series.numFactory().numOf(0.9),
                        series.numFactory().numOf(0.9), series.numFactory().numOf(0.9), series.numFactory().numOf(0.9),
                        series.numFactory().numOf(0.9), series.numFactory().numOf(0.9), "stub"), List.of());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(detector)
                .swingFilter(filter)
                .minConfidence(0.0)
                .confidenceModel(model)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.rawSwings()).hasSize(3);
        assertThat(base.processedSwings()).hasSize(3);
        assertThat(base.processedSwings().subList(0, 2)).containsExactlyElementsOf(swings.subList(0, 2));
        assertThat(base.processedSwings().getLast().fromIndex()).isEqualTo(swings.get(1).toIndex());
        assertThat(base.scenarios().isEmpty()).isFalse();
        assertThat(base.confidenceBreakdowns()).hasSize(base.scenarios().size());
    }

    @Test
    void usesAllProcessedSwingsByDefaultForScenarioGeneration() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), degree),
                new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(110), degree),
                new ElliottSwing(4, 6, factory.numOf(110), factory.numOf(130), degree),
                new ElliottSwing(6, 8, factory.numOf(130), factory.numOf(118), degree),
                new ElliottSwing(8, 10, factory.numOf(118), factory.numOf(140), degree),
                new ElliottSwing(10, 12, factory.numOf(140), factory.numOf(126), degree));

        SwingDetector detector = (s, index, deg) -> SwingDetectorResult.fromSwings(swings);
        ConfidenceModel model = (input, phase, channel,
                type) -> new ElliottConfidenceBreakdown(new ElliottConfidence(series.numFactory().numOf(0.9),
                        series.numFactory().numOf(0.9), series.numFactory().numOf(0.9), series.numFactory().numOf(0.9),
                        series.numFactory().numOf(0.9), series.numFactory().numOf(0.9), "stub"), List.of());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(detector)
                .minConfidence(0.0)
                .confidenceModel(model)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.rawSwings()).hasSize(6);
        assertThat(base.processedSwings()).hasSize(6);
    }

    @Test
    void ranksMoreCompleteScenarioAheadWhenConfidenceIsTied() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottScenario partial = scenario(factory, "partial", ElliottPhase.WAVE3, 0.72,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(138), ElliottDegree.PRIMARY)),
                factory.numOf(94), 0.40);
        ElliottScenario complete = scenario(factory, "complete", ElliottPhase.WAVE5, 0.72,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(144), ElliottDegree.PRIMARY),
                        new ElliottSwing(6, 8, factory.numOf(144), factory.numOf(126), ElliottDegree.PRIMARY),
                        new ElliottSwing(8, 10, factory.numOf(126), factory.numOf(156), ElliottDegree.PRIMARY)),
                factory.numOf(92), 0.95);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(partial, complete), series.getEndIndex());
        ElliottAnalysisResult analysisResult = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                complete.swings(), complete.swings(), scenarios, Map.of(), null, scenarios.trendBias());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);

        assertThat(result.rankedBaseScenarios()).hasSize(2);
        assertThat(result.rankedBaseScenarios().getFirst().scenario().id()).isEqualTo("complete");
    }

    @Test
    void ranksFullSpanTerminalScenarioAheadWhenConfidenceIsClose() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottScenario partial = scenario(factory, "partial-near-end", ElliottPhase.WAVE3, 0.76,
                List.of(new ElliottSwing(2, 4, factory.numOf(105), factory.numOf(124), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(124), factory.numOf(112), ElliottDegree.PRIMARY),
                        new ElliottSwing(6, 10, factory.numOf(112), factory.numOf(144), ElliottDegree.PRIMARY)),
                factory.numOf(101), 0.60);
        ElliottScenario complete = scenario(factory, "complete-terminal", ElliottPhase.WAVE5, 0.74,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(146), ElliottDegree.PRIMARY),
                        new ElliottSwing(6, 8, factory.numOf(146), factory.numOf(130), ElliottDegree.PRIMARY),
                        new ElliottSwing(8, 10, factory.numOf(130), factory.numOf(160), ElliottDegree.PRIMARY)),
                factory.numOf(95), 0.95);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(partial, complete), series.getEndIndex());
        ElliottAnalysisResult analysisResult = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                complete.swings(), complete.swings(), scenarios, Map.of(), null, scenarios.trendBias());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);

        assertThat(result.rankedBaseScenarios()).hasSize(2);
        assertThat(result.rankedBaseScenarios().getFirst().scenario().id()).isEqualTo("complete-terminal");
    }

    @Test
    void ranksBalancedSpacingAheadWhenConfidenceIsTied() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottScenario clustered = scenario(factory, "clustered", ElliottPhase.WAVE5, 0.73,
                List.of(new ElliottSwing(0, 1, factory.hundred(), factory.numOf(112), ElliottDegree.PRIMARY),
                        new ElliottSwing(1, 2, factory.numOf(112), factory.numOf(106), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 3, factory.numOf(106), factory.numOf(126), ElliottDegree.PRIMARY),
                        new ElliottSwing(3, 4, factory.numOf(126), factory.numOf(118), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 10, factory.numOf(118), factory.numOf(154), ElliottDegree.PRIMARY)),
                factory.numOf(95), 0.92);
        ElliottScenario balanced = scenario(factory, "balanced", ElliottPhase.WAVE5, 0.73,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(114), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(114), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(136), ElliottDegree.PRIMARY),
                        new ElliottSwing(6, 8, factory.numOf(136), factory.numOf(126), ElliottDegree.PRIMARY),
                        new ElliottSwing(8, 10, factory.numOf(126), factory.numOf(154), ElliottDegree.PRIMARY)),
                factory.numOf(94), 0.92);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(clustered, balanced), series.getEndIndex());
        ElliottAnalysisResult analysisResult = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                balanced.swings(), balanced.swings(), scenarios, Map.of(), null, scenarios.trendBias());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);

        assertThat(result.rankedBaseScenarios()).hasSize(2);
        assertThat(result.rankedBaseScenarios().getFirst().scenario().id()).isEqualTo("balanced");
    }

    @Test
    void extendsTerminalSwingToEvaluationBarWhenTrendKeepsRunning() {
        BarSeries series = buildTerminalExtensionSeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), degree),
                new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), degree),
                new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(144), degree),
                new ElliottSwing(6, 8, factory.numOf(144), factory.numOf(126), degree));

        SwingDetector detector = (s, index, deg) -> SwingDetectorResult.fromSwings(swings);
        ConfidenceModel model = (input, phase, channel,
                type) -> new ElliottConfidenceBreakdown(new ElliottConfidence(series.numFactory().numOf(0.8),
                        series.numFactory().numOf(0.8), series.numFactory().numOf(0.8), series.numFactory().numOf(0.8),
                        series.numFactory().numOf(0.8), series.numFactory().numOf(0.8), "stub"), List.of());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(detector)
                .patternSet(PatternSet.of(ScenarioType.IMPULSE))
                .minConfidence(0.0)
                .confidenceModel(model)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.processedSwings()).hasSize(5);
        assertThat(base.processedSwings().getLast().toIndex()).isEqualTo(series.getEndIndex());
        assertThat(result.rankedBaseScenarios())
                .anyMatch(assessment -> assessment.scenario().currentPhase() == ElliottPhase.WAVE5
                        && assessment.scenario().swings().getLast().toIndex() == series.getEndIndex());
    }

    @Test
    void fullHistoryModeUsesLighterDefaultSwingFilterOnLongSeries() {
        BarSeries series = buildLongHistorySeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 50, factory.hundred(), factory.numOf(110), degree),
                new ElliottSwing(50, 100, factory.numOf(110), factory.numOf(103), degree),
                new ElliottSwing(100, 150, factory.numOf(103), factory.numOf(117), degree),
                new ElliottSwing(150, 200, factory.numOf(117), factory.numOf(108), degree),
                new ElliottSwing(200, 250, factory.numOf(108), factory.numOf(1000), degree),
                new ElliottSwing(250, 299, factory.numOf(1000), factory.numOf(950), degree));

        SwingDetector detector = (ignoredSeries, index, ignoredDegree) -> SwingDetectorResult.fromSwings(swings);
        ConfidenceModel model = (input, phase, channel,
                type) -> new ElliottConfidenceBreakdown(new ElliottConfidence(series.numFactory().numOf(0.8),
                        series.numFactory().numOf(0.8), series.numFactory().numOf(0.8), series.numFactory().numOf(0.8),
                        series.numFactory().numOf(0.8), series.numFactory().numOf(0.8), "stub"), List.of());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(detector)
                .scenarioSwingWindow(0)
                .patternSet(PatternSet.of(ScenarioType.IMPULSE))
                .minConfidence(0.0)
                .confidenceModel(model)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.rawSwings()).hasSize(6);
        assertThat(base.processedSwings()).hasSize(6);
        assertThat(base.processedSwings().get(1).amplitude()).isEqualByComparingTo(factory.numOf(7));
        assertThat(base.processedSwings().getFirst().fromIndex()).isEqualTo(0);
    }

    @Test
    void fullHistoryModeUsesLighterDefaultSwingCompressorOnLongSeries() {
        BarSeries series = buildLongHistorySeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 50, factory.hundred(), factory.numOf(101.0), degree),
                new ElliottSwing(50, 100, factory.numOf(101.0), factory.numOf(100.2), degree),
                new ElliottSwing(100, 150, factory.numOf(100.2), factory.numOf(101.4), degree),
                new ElliottSwing(150, 200, factory.numOf(101.4), factory.numOf(100.6), degree),
                new ElliottSwing(200, 250, factory.numOf(100.6), factory.numOf(140.0), degree),
                new ElliottSwing(250, 299, factory.numOf(140.0), factory.numOf(130.0), degree));

        SwingDetector detector = (ignoredSeries, index, ignoredDegree) -> SwingDetectorResult.fromSwings(swings);
        SwingFilter passThroughFilter = detected -> List.copyOf(detected);
        ConfidenceModel model = (input, phase, channel,
                type) -> new ElliottConfidenceBreakdown(new ElliottConfidence(series.numFactory().numOf(0.8),
                        series.numFactory().numOf(0.8), series.numFactory().numOf(0.8), series.numFactory().numOf(0.8),
                        series.numFactory().numOf(0.8), series.numFactory().numOf(0.8), "stub"), List.of());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(detector)
                .swingFilter(passThroughFilter)
                .scenarioSwingWindow(0)
                .patternSet(PatternSet.of(ScenarioType.IMPULSE))
                .minConfidence(0.0)
                .confidenceModel(model)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.rawSwings()).hasSize(6);
        assertThat(base.processedSwings()).hasSize(6);
        assertThat(base.processedSwings().getFirst().fromIndex()).isEqualTo(0);
    }

    @Test
    void fullHistoryModeClipsPublicScenarioSetToRankedSelections() {
        BarSeries series = buildLongHistorySeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 25, factory.numOf(100), factory.numOf(116), degree),
                new ElliottSwing(25, 50, factory.numOf(116), factory.numOf(107), degree),
                new ElliottSwing(50, 75, factory.numOf(107), factory.numOf(128), degree),
                new ElliottSwing(75, 100, factory.numOf(128), factory.numOf(118), degree),
                new ElliottSwing(100, 125, factory.numOf(118), factory.numOf(143), degree),
                new ElliottSwing(125, 150, factory.numOf(143), factory.numOf(132), degree),
                new ElliottSwing(150, 175, factory.numOf(132), factory.numOf(162), degree),
                new ElliottSwing(175, 200, factory.numOf(162), factory.numOf(149), degree),
                new ElliottSwing(200, 225, factory.numOf(149), factory.numOf(182), degree),
                new ElliottSwing(225, 250, factory.numOf(182), factory.numOf(168), degree),
                new ElliottSwing(250, 275, factory.numOf(168), factory.numOf(205), degree),
                new ElliottSwing(275, 299, factory.numOf(205), factory.numOf(189), degree));

        SwingDetector detector = (ignoredSeries, index, ignoredDegree) -> SwingDetectorResult.fromSwings(swings);
        ConfidenceModel laterStartBiasedModel = (input, phase, channel, type) -> {
            double rawScore = 0.2 + (input.getFirst().fromIndex() / 250.0);
            double boundedScore = Math.min(0.95, rawScore);
            ElliottConfidence confidence = new ElliottConfidence(factory.numOf(boundedScore),
                    factory.numOf(boundedScore), factory.numOf(boundedScore), factory.numOf(boundedScore),
                    factory.numOf(boundedScore), factory.numOf(boundedScore),
                    "Later starts receive higher raw confidence");
            return new ElliottConfidenceBreakdown(confidence, List.of());
        };

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .maxScenarios(2)
                .swingDetector(detector)
                .scenarioSwingWindow(0)
                .patternSet(PatternSet.of(ScenarioType.IMPULSE))
                .minConfidence(0.0)
                .confidenceModel(laterStartBiasedModel)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.scenarios().all()).hasSize(2);
        assertThat(result.rankedBaseScenarios()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(base.scenarios().all().stream().map(ElliottScenario::id).toList()).containsExactlyElementsOf(
                result.rankedBaseScenarios().stream().limit(2).map(assessment -> assessment.scenario().id()).toList());
    }

    @Test
    void analyzeWindowRebasesScenarioIndicesToRootSeries() {
        BarSeries series = buildLongHistorySeries();
        NumFactory factory = series.numFactory();
        ElliottScenario localScenario = scenario(factory, "windowed", ElliottPhase.WAVE5, 0.82,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 5, factory.numOf(108), factory.numOf(138), ElliottDegree.PRIMARY)),
                factory.numOf(92), 0.95);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(localScenario), 5);
        ElliottAnalysisResult analysisSnapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, 5,
                localScenario.swings(), localScenario.swings(), scenarios, Map.of(), null, scenarios.trendBias());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisSnapshot)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyzeWindow(series, 40, 45);
        ElliottAnalysisResult base = result.analysisFor(ElliottDegree.PRIMARY).orElseThrow().analysis();

        assertThat(base.index()).isEqualTo(45);
        assertThat(base.processedSwings().getFirst().fromIndex()).isEqualTo(40);
        assertThat(base.processedSwings().getLast().toIndex()).isEqualTo(45);
        assertThat(result.rankedBaseScenarios().getFirst().scenario().startIndex()).isEqualTo(40);
        assertThat(result.rankedBaseScenarios().getFirst().scenario().swings().getLast().toIndex()).isEqualTo(45);
    }

    @Test
    void analyzeWindowAnchorsNearBoundaryScenariosToRequestedSpan() {
        BarSeries series = buildLongHistorySeries();
        NumFactory factory = series.numFactory();
        ElliottScenario localScenario = scenario(factory, "near-boundary", ElliottPhase.WAVE5, 0.82,
                List.of(new ElliottSwing(2, 4, factory.numOf(105), factory.numOf(124), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(124), factory.numOf(112), ElliottDegree.PRIMARY),
                        new ElliottSwing(6, 8, factory.numOf(112), factory.numOf(144), ElliottDegree.PRIMARY),
                        new ElliottSwing(8, 10, factory.numOf(144), factory.numOf(130), ElliottDegree.PRIMARY),
                        new ElliottSwing(10, 12, factory.numOf(130), factory.numOf(160), ElliottDegree.PRIMARY)),
                factory.numOf(92), 0.95);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(localScenario), 12);
        ElliottAnalysisResult analysisSnapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, 12,
                localScenario.swings(), localScenario.swings(), scenarios, Map.of(), null, scenarios.trendBias());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisSnapshot)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyzeWindow(series, 40, 52);
        ElliottScenario anchoredScenario = result
                .rankedBaseScenariosForSpan(40, 52, ScenarioType.IMPULSE, ElliottPhase.WAVE5, 5, null, 3)
                .getFirst()
                .scenario();

        assertThat(anchoredScenario.startIndex()).isEqualTo(40);
        assertThat(anchoredScenario.swings().getFirst().fromIndex()).isEqualTo(40);
        assertThat(anchoredScenario.swings().getLast().toIndex()).isEqualTo(52);
    }

    @Test
    void analyzeWindowPreservesRequestedStartAcrossLongMacroHistory() {
        BarSeries series = buildLongHistorySeries();
        NumFactory factory = series.numFactory();
        ElliottScenario localScenario = scenario(factory, "long-history-preserved-start", ElliottPhase.WAVE5, 0.84,
                List.of(new ElliottSwing(2, 9, factory.numOf(170), factory.numOf(132), ElliottDegree.PRIMARY),
                        new ElliottSwing(9, 20, factory.numOf(132), factory.numOf(92), ElliottDegree.PRIMARY),
                        new ElliottSwing(20, 50, factory.numOf(92), factory.numOf(128), ElliottDegree.PRIMARY)),
                factory.numOf(112), 0.95, ScenarioType.IMPULSE);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(localScenario), 50);
        ElliottAnalysisResult analysisSnapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, 50,
                localScenario.swings(), localScenario.swings(), scenarios, Map.of(), null, scenarios.trendBias());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisSnapshot)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyzeWindow(series, 200, 250);

        ElliottAnalysisResult base = result.analysisFor(ElliottDegree.PRIMARY).orElseThrow().analysis();
        ElliottScenario anchoredScenario = result.rankedBaseScenarios().getFirst().scenario();

        assertThat(base.index()).isEqualTo(250);
        assertThat(anchoredScenario.startIndex()).isEqualTo(200);
        assertThat(anchoredScenario.swings().getFirst().fromIndex()).isEqualTo(200);
        assertThat(anchoredScenario.swings().getLast().toIndex()).isEqualTo(250);
    }

    @Test
    void analyzeUsesLogicProfileToDriveRankingBlendPreference() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottScenario confidencePreferred = scenario(factory, "confidence-preferred", ElliottPhase.CORRECTIVE_C, 0.95,
                List.of(new ElliottSwing(0, 2, factory.numOf(130), factory.numOf(110), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(110), factory.numOf(150), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(150), factory.numOf(105), ElliottDegree.PRIMARY)),
                factory.numOf(95), 0.95, ScenarioType.CORRECTIVE_ZIGZAG);
        ElliottScenario crossPreferred = scenario(factory, "cross-preferred", ElliottPhase.CORRECTIVE_C, 0.50,
                List.of(new ElliottSwing(0, 2, factory.numOf(110), factory.numOf(140), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(140), factory.numOf(95), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(95), factory.numOf(145), ElliottDegree.PRIMARY)),
                factory.numOf(95), 0.95, ScenarioType.CORRECTIVE_ZIGZAG);
        ElliottScenarioSet baseScenarios = ElliottScenarioSet.of(List.of(confidencePreferred, crossPreferred), 6);

        ElliottScenario supportingScenario = scenario(factory, "profile-supporting-corrective",
                ElliottPhase.CORRECTIVE_C, 0.98,
                List.of(new ElliottSwing(0, 2, factory.numOf(110), factory.numOf(140), ElliottDegree.INTERMEDIATE),
                        new ElliottSwing(2, 4, factory.numOf(140), factory.numOf(95), ElliottDegree.INTERMEDIATE),
                        new ElliottSwing(4, 6, factory.numOf(95), factory.numOf(145), ElliottDegree.INTERMEDIATE)),
                factory.numOf(80), 0.98, ElliottDegree.INTERMEDIATE, ScenarioType.CORRECTIVE_ZIGZAG);
        ElliottScenarioSet supportingScenarios = ElliottScenarioSet.of(List.of(supportingScenario), 6);

        ElliottAnalysisResult primarySnapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                baseScenarios.all().getFirst().swings(), baseScenarios.all().getFirst().swings(), baseScenarios,
                Map.of(), null, baseScenarios.trendBias());
        ElliottAnalysisResult supportSnapshot = new ElliottAnalysisResult(ElliottDegree.INTERMEDIATE,
                series.getEndIndex(), supportingScenario.swings(), supportingScenario.swings(), supportingScenarios,
                Map.of(), null, supportingScenarios.trendBias());

        ElliottWaveAnalysisRunner confidenceWeightedRunner = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, degree) -> ElliottDegree.PRIMARY.equals(degree) ? primarySnapshot
                        : supportSnapshot)
                .logicProfile(ElliottLogicProfile.BTC_RELAXED_IMPULSE)
                .minConfidence(0.0)
                .build();
        ElliottWaveAnalysisResult confidenceWeightedResult = confidenceWeightedRunner.analyze(series);

        ElliottWaveAnalysisRunner crossWeightedRunner = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, degree) -> ElliottDegree.PRIMARY.equals(degree) ? primarySnapshot
                        : supportSnapshot)
                .logicProfile(ElliottLogicProfile.ANCHOR_FIRST_HYBRID)
                .minConfidence(0.0)
                .build();
        ElliottWaveAnalysisResult crossWeightedResult = crossWeightedRunner.analyze(series);

        ElliottWaveAnalysisResult.BaseScenarioAssessment confidencePreferredConfidenceWeighted = confidenceWeightedResult
                .rankedBaseScenarios()
                .stream()
                .filter(assessment -> "confidence-preferred".equals(assessment.scenario().id()))
                .findFirst()
                .orElseThrow();
        ElliottWaveAnalysisResult.BaseScenarioAssessment crossPreferredConfidenceWeighted = confidenceWeightedResult
                .rankedBaseScenarios()
                .stream()
                .filter(assessment -> "cross-preferred".equals(assessment.scenario().id()))
                .findFirst()
                .orElseThrow();
        ElliottWaveAnalysisResult.BaseScenarioAssessment confidencePreferredCrossWeighted = crossWeightedResult
                .rankedBaseScenarios()
                .stream()
                .filter(assessment -> "confidence-preferred".equals(assessment.scenario().id()))
                .findFirst()
                .orElseThrow();
        ElliottWaveAnalysisResult.BaseScenarioAssessment crossPreferredCrossWeighted = crossWeightedResult
                .rankedBaseScenarios()
                .stream()
                .filter(assessment -> "cross-preferred".equals(assessment.scenario().id()))
                .findFirst()
                .orElseThrow();

        assertThat(confidenceWeightedResult.rankedBaseScenarios().getFirst().scenario().id())
                .isEqualTo("confidence-preferred");
        assertThat(crossPreferredCrossWeighted.compositeScore())
                .isGreaterThan(crossPreferredConfidenceWeighted.compositeScore());
        assertThat(confidencePreferredCrossWeighted.compositeScore())
                .isLessThan(confidencePreferredConfidenceWeighted.compositeScore());
    }

    @Test
    void analyzeCurrentCycleReturnsAlternatingAnchoredBullishCandidate() {
        BarSeries series = buildCurrentCycleSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult.CurrentCycleAssessment result = analysis.analyzeCurrentCycle(series);

        assertThat(result.primary()).isNotNull();
        assertThat(result.primary().currentPhase()).isEqualTo(ElliottPhase.WAVE3);
        assertThat(result.startIndex()).isEqualTo(series.getBeginIndex());
        assertThat(result.candidates()).isNotEmpty();
        ElliottScenario scenario = result.primary().scenario();
        assertThat(result.primary().invalidationPrice()).isEqualByComparingTo(scenario.swings().getFirst().fromPrice());
        assertThat(result.primary().phaseInvalidationPrice()).isEqualByComparingTo(scenario.swings().get(1).toPrice());
        assertThat(scenario.swings()).hasSize(3);
        assertThat(scenario.swings().getFirst().fromIndex()).isEqualTo(series.getBeginIndex());
        assertThat(scenario.swings().getLast().toIndex()).isEqualTo(series.getEndIndex());
        for (int index = 1; index < scenario.swings().size(); index++) {
            ElliottSwing previous = scenario.swings().get(index - 1);
            ElliottSwing current = scenario.swings().get(index);
            assertThat(previous.isRising()).isNotEqualTo(current.isRising());
            assertThat(previous.toIndex()).isEqualTo(current.fromIndex());
            assertThat(previous.toPrice()).isEqualByComparingTo(current.fromPrice());
        }
    }

    @Test
    void buildMacroPivotGraphCarriesProcessedPivotMetadata() {
        BarSeries series = buildCurrentCycleSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(2)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottAnalysisResult snapshot = analysis.analyze(series)
                .analysisFor(ElliottDegree.PRIMARY)
                .orElseThrow()
                .analysis();
        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);

        assertThat(graph.higherDegrees()).isEqualTo(2);
        assertThat(graph.lowerDegrees()).isEqualTo(1);
        assertThat(graph.pivots()).hasSize(4);
        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex)).containsExactly(0, 4, 7,
                9);
        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::highPivot)).containsExactly(false,
                true, false, true);
        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::degree))
                .containsOnly(ElliottDegree.PRIMARY);
        assertThat(graph.pivots().getFirst().time()).isEqualTo(series.getBar(0).getEndTime());
        assertThat(graph.pivots().getLast().time()).isEqualTo(series.getLastBar().getEndTime());
        assertThat(graph.pivots().getFirst().price()).isEqualByComparingTo(factory.numOf(100));
        assertThat(graph.pivots().get(1).price()).isEqualByComparingTo(factory.numOf(180));
        assertThat(graph.pivots().get(2).price()).isEqualByComparingTo(factory.numOf(140));
        assertThat(graph.pivots().getLast().price()).isEqualByComparingTo(factory.numOf(210));
    }

    @Test
    void buildMacroPivotGraphPrunesWeakInteriorPivotsOnDenseHistory() {
        BarSeries series = buildDenseMacroPivotSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> denseMacroPivotSnapshot(window, factory))
                .build();

        ElliottAnalysisResult snapshot = analysis.analyze(series)
                .analysisFor(ElliottDegree.PRIMARY)
                .orElseThrow()
                .analysis();
        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);

        assertThat(graph.pivots()).hasSizeLessThan(29);
        assertThat(graph.pivots()).hasSizeLessThanOrEqualTo(24);
        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex)).contains(0, 8, 16, 24,
                28);
    }

    @Test
    void buildMacroPivotGraphRetainsBroadHistoryCoverageAcrossBuckets() {
        BarSeries series = buildBucketCoverageMacroPivotSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> bucketCoverageMacroPivotSnapshot(window, factory))
                .build();

        ElliottAnalysisResult snapshot = analysis.analyze(series)
                .analysisFor(ElliottDegree.PRIMARY)
                .orElseThrow()
                .analysis();
        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);
        List<Integer> retained = graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex).toList();

        assertThat(graph.pivots()).hasSizeLessThanOrEqualTo(24);
        assertThat(retained).contains(0, 32);
        assertThat(retained.stream().filter(index -> index > 0 && index < 10).count()).isGreaterThanOrEqualTo(2);
        assertThat(retained.stream().filter(index -> index >= 10 && index < 20).count()).isGreaterThanOrEqualTo(2);
        assertThat(retained.stream().filter(index -> index >= 20 && index < 30).count()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void buildMacroPivotGraphMergesRawSwingsToRetainEarlierMacroTurns() {
        BarSeries series = buildMergedRawMacroPivotSeries();
        NumFactory factory = series.numFactory();
        List<ElliottSwing> rawSwings = List.of(
                new ElliottSwing(0, 4, factory.numOf(100), factory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 8, factory.numOf(150), factory.numOf(80), ElliottDegree.PRIMARY),
                new ElliottSwing(8, 12, factory.numOf(80), factory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(12, 16, factory.numOf(130), factory.numOf(95), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 20, factory.numOf(95), factory.numOf(160), ElliottDegree.PRIMARY),
                new ElliottSwing(20, 24, factory.numOf(160), factory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(24, 28, factory.numOf(110), factory.numOf(180), ElliottDegree.PRIMARY));
        List<ElliottSwing> processedSwings = rawSwings.subList(4, rawSwings.size());
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        ElliottAnalysisResult snapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                rawSwings, processedSwings, empty, Map.of(), null, empty.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> snapshot)
                .build();

        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);

        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex)).contains(8, 20, 24, 28);
    }

    @Test
    void buildMacroPivotGraphPreservesRawSwingEndpointsCollapsedByPivotNormalization() {
        BarSeries series = buildCollapsedNormalizationMacroPivotSeries();
        NumFactory factory = series.numFactory();
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 8, factory.numOf(100), factory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(8, 14, factory.numOf(180), factory.numOf(70), ElliottDegree.PRIMARY),
                new ElliottSwing(14, 16, factory.numOf(70), factory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 18, factory.numOf(150), factory.numOf(60), ElliottDegree.PRIMARY),
                new ElliottSwing(18, 24, factory.numOf(60), factory.numOf(190), ElliottDegree.PRIMARY));
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        ElliottAnalysisResult snapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings,
                swings, empty, Map.of(), null, empty.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> snapshot)
                .build();

        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);

        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex)).contains(14, 16, 18);
    }

    @Test
    void buildMacroPivotGraphRetainsDominantBucketLowAlongsideDeeperBucketLow() {
        BarSeries series = buildBucketedDominanceMacroPivotSeries();
        NumFactory factory = series.numFactory();
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 8, factory.numOf(100), factory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(8, 14, factory.numOf(180), factory.numOf(62), ElliottDegree.PRIMARY),
                new ElliottSwing(14, 16, factory.numOf(62), factory.numOf(176), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 18, factory.numOf(176), factory.numOf(60), ElliottDegree.PRIMARY),
                new ElliottSwing(18, 24, factory.numOf(60), factory.numOf(100), ElliottDegree.PRIMARY),
                new ElliottSwing(24, 28, factory.numOf(100), factory.numOf(72), ElliottDegree.PRIMARY),
                new ElliottSwing(28, 32, factory.numOf(72), factory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(32, 36, factory.numOf(120), factory.numOf(78), ElliottDegree.PRIMARY),
                new ElliottSwing(36, 40, factory.numOf(78), factory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(40, 44, factory.numOf(130), factory.numOf(84), ElliottDegree.PRIMARY),
                new ElliottSwing(44, 48, factory.numOf(84), factory.numOf(140), ElliottDegree.PRIMARY),
                new ElliottSwing(48, 52, factory.numOf(140), factory.numOf(90), ElliottDegree.PRIMARY),
                new ElliottSwing(52, 56, factory.numOf(90), factory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(56, 60, factory.numOf(150), factory.numOf(96), ElliottDegree.PRIMARY),
                new ElliottSwing(60, 64, factory.numOf(96), factory.numOf(160), ElliottDegree.PRIMARY),
                new ElliottSwing(64, 68, factory.numOf(160), factory.numOf(102), ElliottDegree.PRIMARY),
                new ElliottSwing(68, 72, factory.numOf(102), factory.numOf(170), ElliottDegree.PRIMARY),
                new ElliottSwing(72, 76, factory.numOf(170), factory.numOf(108), ElliottDegree.PRIMARY));
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        ElliottAnalysisResult snapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings,
                swings, empty, Map.of(), null, empty.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> snapshot)
                .build();

        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);

        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex)).contains(14, 18);
    }

    @Test
    void buildMacroPivotGraphRetainsPostHighBucketLowWhenEarlierAbsoluteLowExists() {
        BarSeries series = buildPostHighBucketMacroPivotSeries();
        NumFactory factory = series.numFactory();
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 4, factory.numOf(100), factory.numOf(160), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 6, factory.numOf(160), factory.numOf(50), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 8, factory.numOf(50), factory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(8, 14, factory.numOf(180), factory.numOf(70), ElliottDegree.PRIMARY),
                new ElliottSwing(14, 18, factory.numOf(70), factory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(18, 26, factory.numOf(120), factory.numOf(80), ElliottDegree.PRIMARY),
                new ElliottSwing(26, 34, factory.numOf(80), factory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(34, 42, factory.numOf(130), factory.numOf(90), ElliottDegree.PRIMARY),
                new ElliottSwing(42, 50, factory.numOf(90), factory.numOf(140), ElliottDegree.PRIMARY),
                new ElliottSwing(50, 58, factory.numOf(140), factory.numOf(100), ElliottDegree.PRIMARY),
                new ElliottSwing(58, 66, factory.numOf(100), factory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(66, 74, factory.numOf(150), factory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(74, 82, factory.numOf(110), factory.numOf(160), ElliottDegree.PRIMARY),
                new ElliottSwing(82, 90, factory.numOf(160), factory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(90, 98, factory.numOf(120), factory.numOf(170), ElliottDegree.PRIMARY),
                new ElliottSwing(98, 106, factory.numOf(170), factory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(106, 114, factory.numOf(130), factory.numOf(180), ElliottDegree.PRIMARY));
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        ElliottAnalysisResult snapshot = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings,
                swings, empty, Map.of(), null, empty.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner((window, ignoredDegree) -> snapshot)
                .build();

        ElliottWaveAnalysisRunner.MacroPivotGraph graph = analysis.buildMacroPivotGraph(series, snapshot);

        assertThat(graph.pivots().stream().map(ElliottWaveAnalysisRunner.MacroPivot::barIndex)).contains(6, 14);
    }

    @Test
    void analyzeHistoricalStructureUsesNonAdjacentPivotPairsForCompletedCycles() {
        BarSeries series = buildNonAdjacentHistoricalCycleSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> nonAdjacentHistoricalCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult.HistoricalStructureAssessment history = analysis.analyzeHistoricalStructure(series);

        assertThat(history.legs()).hasSize(3);
        assertThat(history.legs().get(0).bullish()).isFalse();
        assertThat(history.legs().get(0).startIndex()).isZero();
        assertThat(history.legs().get(0).endIndex()).isEqualTo(3);
        assertThat(history.legs().get(1).bullish()).isTrue();
        assertThat(history.legs().get(1).startIndex()).isEqualTo(3);
        assertThat(history.legs().get(1).endIndex()).isEqualTo(8);
        assertThat(history.legs().get(2).bullish()).isFalse();
        assertThat(history.legs().get(2).startIndex()).isEqualTo(8);
        assertThat(history.legs().get(2).endIndex()).isEqualTo(11);
        assertThat(history.cycles()).hasSize(1);
        assertThat(history.cycles().getFirst().bullishLeg().startIndex()).isEqualTo(3);
        assertThat(history.cycles().getFirst().bullishLeg().endIndex()).isEqualTo(8);
        assertThat(history.cycles().getFirst().bearishLeg().endIndex()).isEqualTo(11);
    }

    @Test
    void searchCanonicalStructurePrefersAlternatingCoherentPath() {
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, window.numFactory()))
                .build();

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> candidates = List.of(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("leg-1", 0, 4, true, 0.80),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("leg-2", 4, 7, false, 0.74),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("leg-3", 7, 10, true, 0.72),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("shortcut", 0, 6, true, 0.90),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("weak-tail", 6, 10, false, 0.35));

        ElliottWaveAnalysisRunner.CanonicalStructurePath path = analysis.searchCanonicalStructure(candidates)
                .orElseThrow();

        assertThat(path.legs().stream().map(ElliottWaveAnalysisRunner.CanonicalLegCandidate::id))
                .containsExactly("leg-1", "leg-2", "leg-3");
        assertThat(path.score()).isGreaterThan(2.5);
    }

    @Test
    void searchCanonicalStructurePenalizesGappedLegsAgainstContiguousOnes() {
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, window.numFactory()))
                .build();

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> candidates = List.of(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("contiguous-bull", 0, 4, true, 0.78),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("contiguous-bear", 4, 7, false, 0.76),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("gapped-bear", 6, 9, false, 0.80));

        ElliottWaveAnalysisRunner.CanonicalStructurePath path = analysis.searchCanonicalStructure(candidates)
                .orElseThrow();

        assertThat(path.legs().stream().map(ElliottWaveAnalysisRunner.CanonicalLegCandidate::id))
                .containsExactly("contiguous-bull", "contiguous-bear");
    }

    @Test
    void historicalStructureAssessmentPairsCompletedBullBearLegsIntoCycles() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.CanonicalStructurePath path = new ElliottWaveAnalysisRunner.CanonicalStructurePath(
                List.of(new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-1", 0, 4, true, 0.82,
                        historicalAnchoredSelection(factory, "bull-1", ElliottPhase.WAVE5, true, true, 0.82)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-1", 4, 7, false, 0.79,
                                historicalAnchoredSelection(factory, "bear-1", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.79)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-2", 7, 10, true, 0.76,
                                historicalAnchoredSelection(factory, "bull-2", ElliottPhase.WAVE5, true, false, 0.76))),
                2.37);

        ElliottWaveAnalysisResult.HistoricalStructureAssessment structure = analysis
                .historicalStructureAssessment(path);

        assertThat(structure.legs()).hasSize(3);
        assertThat(structure.cycles()).hasSize(1);
        assertThat(structure.cycles().getFirst().bullishLeg().scenario().id()).isEqualTo("bull-1");
        assertThat(structure.cycles().getFirst().bearishLeg().scenario().id()).isEqualTo("bear-1");
        assertThat(structure.cycles().getFirst().bullishLeg().accepted()).isTrue();
        assertThat(structure.cycles().getFirst().bearishLeg().accepted()).isTrue();
        assertThat(structure.legs().getLast().accepted()).isFalse();
    }

    @Test
    void promoteHistoricalMacroCyclesUsesSupportedNonAdjacentPivotPairs() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> candidates = List.of(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("support-bear", 0, 3, false, 0.84,
                        historicalAnchoredSelection(factory, "support-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.84)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("macro-bull", 3, 8, true, 0.88,
                        historicalAnchoredSelection(factory, "macro-bull", ElliottPhase.WAVE5, true, true, 0.88)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("macro-bear", 8, 11, false, 0.84,
                        historicalAnchoredSelection(factory, "macro-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.84)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("recovery-bull", 11, 16, true, 0.86,
                        historicalAnchoredSelection(factory, "recovery-bull", ElliottPhase.WAVE5, true, true, 0.86)));

        List<ElliottWaveAnalysisResult.HistoricalCycleAssessment> cycles = analysis
                .promoteHistoricalMacroCycles(candidates);

        assertThat(cycles).hasSize(1);
        assertThat(cycles.getFirst().bullishLeg().startIndex()).isEqualTo(3);
        assertThat(cycles.getFirst().bullishLeg().endIndex()).isEqualTo(8);
        assertThat(cycles.getFirst().bearishLeg().startIndex()).isEqualTo(8);
        assertThat(cycles.getFirst().bearishLeg().endIndex()).isEqualTo(11);
    }

    @Test
    void collapseHistoricalCycleFamiliesPrefersStrongerFitRepresentativeOverEarlierStart() throws Exception {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.HistoricalCycleCandidate earlier = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("earlier-bull", 0, 8, true, 0.81,
                        historicalAnchoredSelection(factory, "earlier-bull", ElliottPhase.WAVE5, true, true, 0.81)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("shared-bear", 8, 11, false, 0.96,
                        historicalAnchoredSelection(factory, "shared-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.96)),
                2.0110);
        ElliottWaveAnalysisRunner.HistoricalCycleCandidate later = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("later-bull", 3, 8, true, 0.91,
                        historicalAnchoredSelection(factory, "later-bull", ElliottPhase.WAVE5, true, true, 0.91)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("shared-bear", 8, 11, false, 0.96,
                        historicalAnchoredSelection(factory, "shared-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.96)),
                1.9510);

        Method collapseFamilies = ElliottWaveAnalysisRunner.class.getDeclaredMethod("collapseHistoricalCycleFamilies",
                List.class);
        collapseFamilies.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ElliottWaveAnalysisRunner.HistoricalCycleCandidate> collapsed = (List<ElliottWaveAnalysisRunner.HistoricalCycleCandidate>) collapseFamilies
                .invoke(analysis, List.of(earlier, later));

        assertThat(collapsed).hasSize(1);
        assertThat(collapsed.getFirst().bullishLeg().id()).isEqualTo("later-bull");
    }

    @Test
    void pruneSubordinateHistoricalCycleCandidatesDropsNestedCyclesWithinMacroEnvelope() throws Exception {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.HistoricalCycleCandidate nestedCycle = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("nested-bull", 5, 8, true, 0.96,
                        historicalAnchoredSelection(factory, "nested-bull", ElliottPhase.WAVE5, true, true, 0.96)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("nested-bear", 8, 11, false, 0.94,
                        historicalAnchoredSelection(factory, "nested-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.94)),
                1.99);
        ElliottWaveAnalysisRunner.HistoricalCycleCandidate macroCycle = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("macro-bull", 3, 8, true, 0.92,
                        historicalAnchoredSelection(factory, "macro-bull", ElliottPhase.WAVE5, true, true, 0.92)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("macro-bear", 8, 13, false, 0.97,
                        historicalAnchoredSelection(factory, "macro-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.97)),
                2.02);

        Method pruneSubordinateHistoricalCycles = ElliottWaveAnalysisRunner.class
                .getDeclaredMethod("pruneSubordinateHistoricalCycleCandidates", List.class);
        pruneSubordinateHistoricalCycles.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ElliottWaveAnalysisRunner.HistoricalCycleCandidate> retained = (List<ElliottWaveAnalysisRunner.HistoricalCycleCandidate>) pruneSubordinateHistoricalCycles
                .invoke(analysis, List.of(nestedCycle, macroCycle));

        assertThat(retained).hasSize(1);
        assertThat(retained.getFirst().bullishLeg().id()).isEqualTo("macro-bull");
    }

    @Test
    void pruneSubordinateHistoricalCycleCandidatesKeepsBroaderCyclesWithDifferentStartsAndPeaks() throws Exception {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.HistoricalCycleCandidate firstCycle = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("first-bull", 10, 30, true, 0.88,
                        historicalAnchoredSelection(factory, "first-bull", ElliottPhase.WAVE5, true, true, 0.88)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("first-bear", 30, 40, false, 0.85,
                        historicalAnchoredSelection(factory, "first-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.85)),
                1.78);
        ElliottWaveAnalysisRunner.HistoricalCycleCandidate secondCycle = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("second-bull", 40, 60, true, 0.87,
                        historicalAnchoredSelection(factory, "second-bull", ElliottPhase.WAVE5, true, true, 0.87)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("second-bear", 60, 70, false, 0.84,
                        historicalAnchoredSelection(factory, "second-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.84)),
                1.74);
        ElliottWaveAnalysisRunner.HistoricalCycleCandidate broadEnvelope = new ElliottWaveAnalysisRunner.HistoricalCycleCandidate(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("broad-bull", 5, 55, true, 0.90,
                        historicalAnchoredSelection(factory, "broad-bull", ElliottPhase.WAVE5, true, true, 0.90)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("broad-bear", 55, 70, false, 0.88,
                        historicalAnchoredSelection(factory, "broad-bear", ElliottPhase.CORRECTIVE_C, false, true,
                                0.88)),
                1.80);

        Method pruneSubordinateHistoricalCycles = ElliottWaveAnalysisRunner.class
                .getDeclaredMethod("pruneSubordinateHistoricalCycleCandidates", List.class);
        pruneSubordinateHistoricalCycles.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ElliottWaveAnalysisRunner.HistoricalCycleCandidate> retained = (List<ElliottWaveAnalysisRunner.HistoricalCycleCandidate>) pruneSubordinateHistoricalCycles
                .invoke(analysis, List.of(firstCycle, secondCycle, broadEnvelope));

        assertThat(retained).containsExactly(firstCycle, secondCycle, broadEnvelope);
    }

    @Test
    void historicalStructureAssessmentPromotesHighFitFallbackBullLegWhenBearLegIsAccepted() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.CanonicalStructurePath promotablePath = new ElliottWaveAnalysisRunner.CanonicalStructurePath(
                List.of(new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-fallback", 0, 5, true, 0.82,
                        historicalAnchoredSelection(factory, "bull-fallback", ElliottPhase.WAVE5, true, false, 0.82)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-accepted", 5, 8, false, 0.86,
                                historicalAnchoredSelection(factory, "bear-accepted", ElliottPhase.CORRECTIVE_C, false,
                                        true, 0.86))),
                1.68);

        ElliottWaveAnalysisResult.HistoricalStructureAssessment promotableStructure = analysis
                .historicalStructureAssessment(promotablePath);

        assertThat(promotableStructure.cycles()).hasSize(1);
        assertThat(promotableStructure.cycles().getFirst().bullishLeg().accepted()).isFalse();
        assertThat(promotableStructure.cycles().getFirst().bearishLeg().accepted()).isTrue();

        ElliottWaveAnalysisRunner.CanonicalStructurePath fallbackOnlyPath = new ElliottWaveAnalysisRunner.CanonicalStructurePath(
                List.of(new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-fallback", 0, 5, true, 0.82,
                        historicalAnchoredSelection(factory, "bull-fallback", ElliottPhase.WAVE5, true, false, 0.82)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-fallback", 5, 8, false, 0.81,
                                historicalAnchoredSelection(factory, "bear-fallback", ElliottPhase.CORRECTIVE_C, false,
                                        false, 0.81))),
                1.63);

        ElliottWaveAnalysisResult.HistoricalStructureAssessment fallbackOnlyStructure = analysis
                .historicalStructureAssessment(fallbackOnlyPath);

        assertThat(fallbackOnlyStructure.cycles()).isEmpty();
    }

    @Test
    void historicalStructureAssessmentKeepsSubordinateLegsButPromotesOnlyBroadCycles() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.CanonicalStructurePath path = new ElliottWaveAnalysisRunner.CanonicalStructurePath(
                List.of(new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-1", 0, 4, true, 0.84,
                        historicalAnchoredSelection(factory, "bull-1", ElliottPhase.WAVE5, true, true, 0.84)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-1", 4, 8, false, 0.83,
                                historicalAnchoredSelection(factory, "bear-1", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.83)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-2", 8, 12, true, 0.82,
                                historicalAnchoredSelection(factory, "bull-2", ElliottPhase.WAVE5, true, true, 0.82)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-2", 12, 16, false, 0.81,
                                historicalAnchoredSelection(factory, "bear-2", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.81)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-3", 16, 20, true, 0.80,
                                historicalAnchoredSelection(factory, "bull-3", ElliottPhase.WAVE5, true, true, 0.80)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-3", 20, 24, false, 0.79,
                                historicalAnchoredSelection(factory, "bear-3", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.79)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-4", 24, 26, true, 0.78,
                                historicalAnchoredSelection(factory, "bull-4", ElliottPhase.WAVE5, true, true, 0.78)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-4", 26, 28, false, 0.77,
                                historicalAnchoredSelection(factory, "bear-4", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.77)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-5", 28, 30, true, 0.76,
                                historicalAnchoredSelection(factory, "bull-5", ElliottPhase.WAVE5, true, true, 0.76)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-5", 30, 32, false, 0.75,
                                historicalAnchoredSelection(factory, "bear-5", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.75))),
                7.95);

        ElliottWaveAnalysisResult.HistoricalStructureAssessment structure = analysis
                .historicalStructureAssessment(path);

        assertThat(structure.legs()).hasSize(10);
        assertThat(structure.cycles()).hasSize(3);
        assertThat(structure.cycles()
                .stream()
                .map(cycle -> cycle.bullishLeg().scenario().id() + "->" + cycle.bearishLeg().scenario().id()))
                .containsExactly("bull-1->bear-1", "bull-2->bear-2", "bull-3->bear-3");
    }

    @Test
    void historicalStructureAssessmentKeepsAllCyclesWhenNoClearSpanBreakExists() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisRunner.CanonicalStructurePath path = new ElliottWaveAnalysisRunner.CanonicalStructurePath(
                List.of(new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-1", 0, 5, true, 0.84,
                        historicalAnchoredSelection(factory, "bull-1", ElliottPhase.WAVE5, true, true, 0.84)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-1", 5, 9, false, 0.83,
                                historicalAnchoredSelection(factory, "bear-1", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.83)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bull-2", 9, 14, true, 0.82,
                                historicalAnchoredSelection(factory, "bull-2", ElliottPhase.WAVE5, true, true, 0.82)),
                        new ElliottWaveAnalysisRunner.CanonicalLegCandidate("bear-2", 14, 19, false, 0.81,
                                historicalAnchoredSelection(factory, "bear-2", ElliottPhase.CORRECTIVE_C, false, true,
                                        0.81))),
                3.30);

        ElliottWaveAnalysisResult.HistoricalStructureAssessment structure = analysis
                .historicalStructureAssessment(path);

        assertThat(structure.legs()).hasSize(4);
        assertThat(structure.cycles()).hasSize(2);
    }

    @Test
    void selectHistoricalMacroBottomsKeepsEarlierBottomUntilPeakIsReclaimed() throws Exception {
        BarSeries series = buildHistoricalMacroBottomSeries();
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        Method selectHistoricalMacroBottoms = ElliottWaveAnalysisRunner.class
                .getDeclaredMethod("selectHistoricalMacroBottoms", BarSeries.class, List.class);
        selectHistoricalMacroBottoms.setAccessible(true);

        List<ElliottWaveAnalysisResult.HistoricalLegAssessment> bearishLegs = List.of(
                new ElliottWaveAnalysisResult.HistoricalLegAssessment(0, 2, false,
                        historicalAnchoredSelection(factory, "macro-bottom-1", ElliottPhase.CORRECTIVE_C, false, true,
                                0.88).assessment(),
                        true),
                new ElliottWaveAnalysisResult.HistoricalLegAssessment(8, 14, false,
                        historicalAnchoredSelection(factory, "macro-bottom-2", ElliottPhase.CORRECTIVE_C, false, true,
                                0.86).assessment(),
                        true),
                new ElliottWaveAnalysisResult.HistoricalLegAssessment(18, 20, false,
                        historicalAnchoredSelection(factory, "internal-bottom", ElliottPhase.CORRECTIVE_C, false, true,
                                0.84).assessment(),
                        true));

        @SuppressWarnings("unchecked")
        List<ElliottWaveAnalysisResult.HistoricalLegAssessment> macroBottoms = (List<ElliottWaveAnalysisResult.HistoricalLegAssessment>) selectHistoricalMacroBottoms
                .invoke(analysis, series, bearishLegs);

        assertThat(macroBottoms).hasSize(2);
        assertThat(macroBottoms.get(0).endIndex()).isEqualTo(2);
        assertThat(macroBottoms.get(1).endIndex()).isEqualTo(14);
    }

    @Test
    void retainHistoricalCanonicalLegCandidatesKeepsEarliestMeaningfulPromotableLeg() throws Exception {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        Method retainHistoricalCandidates = ElliottWaveAnalysisRunner.class
                .getDeclaredMethod("retainHistoricalCanonicalLegCandidates", List.class, int.class);
        retainHistoricalCandidates.setAccessible(true);

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> rankedCandidates = List.of(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("late-best", 30, 58, false, 0.97,
                        historicalAnchoredSelection(factory, "late-best", ElliottPhase.CORRECTIVE_C, false, true,
                                0.97)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("too-short-noise", 30, 38, false, 0.95,
                        historicalAnchoredSelection(factory, "too-short-noise", ElliottPhase.CORRECTIVE_C, false, true,
                                0.95)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("earliest-meaningful", 30, 42, false, 0.78,
                        historicalAnchoredSelection(factory, "earliest-meaningful", ElliottPhase.CORRECTIVE_C, false,
                                true, 0.78)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("longest-span", 30, 90, false, 0.71,
                        historicalAnchoredSelection(factory, "longest-span", ElliottPhase.CORRECTIVE_C, false, true,
                                0.71)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("fill-1", 30, 54, false, 0.76,
                        historicalAnchoredSelection(factory, "fill-1", ElliottPhase.CORRECTIVE_C, false, false, 0.76)),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("fill-2", 30, 50, false, 0.75,
                        historicalAnchoredSelection(factory, "fill-2", ElliottPhase.CORRECTIVE_C, false, false, 0.75)));

        @SuppressWarnings("unchecked")
        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> retained = (List<ElliottWaveAnalysisRunner.CanonicalLegCandidate>) retainHistoricalCandidates
                .invoke(analysis, rankedCandidates, 3);

        assertThat(retained.stream().map(ElliottWaveAnalysisRunner.CanonicalLegCandidate::id))
                .containsExactly("late-best", "longest-span", "earliest-meaningful");
    }

    @Test
    void boundCanonicalCandidatesCapsSearchFrontier() {
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, window.numFactory()))
                .build();

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 140; index++) {
            double fit = 1.0 - (index / 200.0);
            candidates.add(new ElliottWaveAnalysisRunner.CanonicalLegCandidate("candidate-" + index, index, index + 2,
                    index % 2 == 0, fit));
        }

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> bounded = analysis.boundCanonicalCandidates(candidates);

        assertThat(bounded).hasSize(128);
        assertThat(bounded.stream().map(ElliottWaveAnalysisRunner.CanonicalLegCandidate::id))
                .contains("candidate-0", "candidate-1", "candidate-2")
                .doesNotContain("candidate-139");
    }

    @Test
    void rankCurrentCycleCandidatesWithCanonicalSearchPrefersCanonicalHistoricalPrefix() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        List<ElliottSwing> processedSwings = List.of(
                new ElliottSwing(0, 2, factory.numOf(100), factory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, factory.numOf(130), factory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 8, factory.numOf(110), factory.numOf(170), ElliottDegree.PRIMARY));
        ElliottWaveAnalysisResult.CurrentCycleCandidate standalone = new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                0, factory.numOf(100),
                currentPhaseAssessment(factory, "standalone", ElliottPhase.WAVE4, 0.82, "Bullish 1-2-3-4"), 0.82, 0.82,
                "standalone");
        ElliottWaveAnalysisResult.CurrentCycleCandidate coherent = new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                4, factory.numOf(110),
                currentPhaseAssessment(factory, "coherent", ElliottPhase.WAVE4, 0.70, "Bullish 1-2-3-4"), 0.70, 0.70,
                "coherent");

        List<ElliottWaveAnalysisRunner.CanonicalLegCandidate> historicalCandidates = List.of(
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("historical-bull", 0, 2, true, 0.68),
                new ElliottWaveAnalysisRunner.CanonicalLegCandidate("historical-bear", 2, 4, false, 0.72));

        List<ElliottWaveAnalysisResult.CurrentCycleCandidate> ranked = analysis
                .rankCurrentCycleCandidatesWithCanonicalSearch(List.of(standalone, coherent), processedSwings,
                        historicalCandidates, 8);

        assertThat(ranked.getFirst().startIndex()).isEqualTo(4);
        assertThat(ranked.getFirst().rationale()).contains("canonical path");
        assertThat(ranked.getFirst().totalScore()).isGreaterThan(standalone.totalScore());
    }

    @Test
    void analyzeCurrentCycleRejectsWaveFiveWhenTerminalHighIsNotDominant() {
        BarSeries series = buildMalformedWaveFiveSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> malformedWaveFiveSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult.CurrentCycleAssessment result = analysis.analyzeCurrentCycle(series);

        assertThat(result.primary()).isNotNull();
        assertThat(result.primary().currentPhase()).isEqualTo(ElliottPhase.WAVE4);
        assertThat(result.candidates()).noneMatch(candidate -> candidate.fit().currentPhase() == ElliottPhase.WAVE5);
    }

    @Test
    void discoverCurrentCycleStartCandidatesPreservesEarliestMacroPivotAcrossScorePressure() throws Exception {
        BarSeries series = buildCurrentCycleStartPressureSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner(
                        (window, ignoredDegree) -> window == series ? currentCycleStartPressureSnapshot(window, factory)
                                : currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult fullAnalysis = analysis.analyze(series);
        Method discoverMethod = ElliottWaveAnalysisRunner.class.getDeclaredMethod("discoverCurrentCycleStartCandidates",
                BarSeries.class, ElliottWaveAnalysisResult.class);
        discoverMethod.setAccessible(true);
        Method barIndexMethod = Class
                .forName("org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner$CurrentCycleStartCandidate")
                .getDeclaredMethod("barIndex");
        barIndexMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) discoverMethod.invoke(analysis, series, fullAnalysis);
        List<Integer> candidateIndices = new ArrayList<>(candidates.size());
        for (Object candidate : candidates) {
            candidateIndices.add((Integer) barIndexMethod.invoke(candidate));
        }

        assertThat(candidateIndices).hasSize(16);
        assertThat(candidateIndices).contains(40);
        assertThat(candidateIndices).contains(285);
    }

    @Test
    void fitPartialLegForWindowSupportsBullishImpulseProgressions() {
        BarSeries series = buildCurrentCycleSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> currentCycleSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult windowAnalysis = analysis.analyzeWindow(series, series.getBeginIndex(),
                series.getEndIndex());
        Optional<ElliottWaveAnalysisResult.CurrentPhaseAssessment> fit = analysis.fitPartialLegForWindow(series,
                windowAnalysis, series.getBeginIndex(), series.getEndIndex(), true, 3);

        assertThat(fit).isPresent();
        assertThat(fit.orElseThrow().currentPhase()).isEqualTo(ElliottPhase.WAVE3);
        assertThat(fit.orElseThrow().countLabel()).isEqualTo("Bullish 1-2-3");
        assertThat(fit.orElseThrow().startPrice()).isEqualByComparingTo(series.getBar(0).getLowPrice());
        assertThat(fit.orElseThrow().phaseInvalidationPrice())
                .isEqualByComparingTo(fit.orElseThrow().scenario().swings().get(1).toPrice());
        ElliottScenario scenario = fit.orElseThrow().scenario();
        assertThat(scenario.swings()).hasSize(3);
        assertThat(scenario.swings().getFirst().fromIndex()).isEqualTo(series.getBeginIndex());
        assertThat(scenario.swings().getLast().toIndex()).isEqualTo(series.getEndIndex());
    }

    @Test
    void fitPartialLegForWindowSupportsBearishCorrectiveProgressions() {
        BarSeries series = buildBearishWindowSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> bearishCorrectiveSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult windowAnalysis = analysis.analyzeWindow(series, series.getBeginIndex(),
                series.getEndIndex());
        Optional<ElliottWaveAnalysisResult.CurrentPhaseAssessment> fit = analysis.fitPartialLegForWindow(series,
                windowAnalysis, series.getBeginIndex(), series.getEndIndex(), false, 3);

        assertThat(fit).isPresent();
        assertThat(fit.orElseThrow().currentPhase()).isEqualTo(ElliottPhase.CORRECTIVE_C);
        assertThat(fit.orElseThrow().countLabel()).isEqualTo("Bearish A-B-C");
        assertThat(fit.orElseThrow().startPrice()).isEqualByComparingTo(series.getBar(0).getHighPrice());
        assertThat(fit.orElseThrow().phaseInvalidationPrice())
                .isEqualByComparingTo(fit.orElseThrow().invalidationPrice());
        ElliottScenario scenario = fit.orElseThrow().scenario();
        assertThat(scenario.swings()).hasSize(3);
        assertThat(scenario.swings().get(1).toIndex()).isEqualTo(4);
        assertThat(scenario.swings().get(1).toPrice()).isEqualByComparingTo(series.getBar(4).getHighPrice());
    }

    @Test
    void currentCycleAssessmentDistinctCandidatesKeepsFirstUniqueScenarioIds() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottWaveAnalysisResult.CurrentCycleCandidate duplicateLeader = new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                0, factory.hundred(),
                currentPhaseAssessment(factory, "duplicate", ElliottPhase.WAVE3, 0.82, "Bullish 1-2-3"), 0.80, 0.92,
                "duplicate leader");
        ElliottWaveAnalysisResult.CurrentCycleCandidate duplicateAlternate = new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                1, factory.hundred(),
                currentPhaseAssessment(factory, "duplicate", ElliottPhase.WAVE3, 0.78, "Bullish 1-2-3"), 0.78, 0.88,
                "duplicate alternate");
        ElliottWaveAnalysisResult.CurrentCycleCandidate uniqueSecond = new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                2, factory.hundred(),
                currentPhaseAssessment(factory, "unique-second", ElliottPhase.WAVE4, 0.74, "Bullish 1-2-3-4"), 0.76,
                0.84, "unique second");
        ElliottWaveAnalysisResult.CurrentCycleCandidate uniqueThird = new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                3, factory.hundred(),
                currentPhaseAssessment(factory, "unique-third", ElliottPhase.WAVE5, 0.70, "Bullish 1-2-3-4-5"), 0.74,
                0.80, "unique third");
        ElliottWaveAnalysisResult.CurrentCycleAssessment assessment = new ElliottWaveAnalysisResult.CurrentCycleAssessment(
                0, duplicateLeader.fit(), uniqueSecond.fit(),
                List.of(duplicateLeader, duplicateAlternate, uniqueSecond, uniqueThird));

        List<ElliottWaveAnalysisResult.CurrentCycleCandidate> distinct = assessment.distinctCandidates(3);

        assertThat(distinct).extracting(candidate -> candidate.fit().scenario().id())
                .containsExactly("duplicate", "unique-second", "unique-third");
        assertThat(distinct).extracting(ElliottWaveAnalysisResult.CurrentCycleCandidate::startIndex)
                .containsExactly(0, 2, 3);
    }

    @Test
    void analyzeCurrentCycleSnapsWaveThreePeakToDominantSpanHigh() {
        BarSeries series = buildWaveFourNormalizationSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> waveFourNormalizationSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult.CurrentCycleAssessment result = analysis.analyzeCurrentCycle(series);

        assertThat(result.primary()).isNotNull();
        assertThat(result.primary().currentPhase()).isEqualTo(ElliottPhase.WAVE4);
        ElliottScenario scenario = result.primary().scenario();
        assertThat(result.primary().invalidationPrice()).isEqualByComparingTo(scenario.swings().getFirst().fromPrice());
        assertThat(result.primary().phaseInvalidationPrice())
                .isEqualByComparingTo(scenario.swings().getFirst().toPrice());
        assertThat(scenario.swings()).hasSize(4);
        assertThat(scenario.swings().get(2).toIndex()).isEqualTo(8);
        assertThat(scenario.swings().get(2).toPrice()).isEqualByComparingTo(series.getBar(8).getHighPrice());
    }

    @Test
    void analyzeCurrentCycleSnapsWaveOnePeakToDominantSpanHigh() {
        BarSeries series = buildWaveTwoNormalizationSeries();
        NumFactory factory = series.numFactory();
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((window, ignoredDegree) -> waveTwoNormalizationSnapshot(window, factory))
                .build();

        ElliottWaveAnalysisResult.CurrentCycleAssessment result = analysis.analyzeCurrentCycle(series);

        assertThat(result.primary()).isNotNull();
        assertThat(result.primary().currentPhase()).isEqualTo(ElliottPhase.WAVE2);
        ElliottScenario scenario = result.primary().scenario();
        assertThat(result.primary().invalidationPrice()).isEqualByComparingTo(scenario.swings().getFirst().fromPrice());
        assertThat(result.primary().phaseInvalidationPrice())
                .isEqualByComparingTo(scenario.swings().getFirst().fromPrice());
        assertThat(scenario.swings()).hasSize(2);
        assertThat(scenario.swings().getFirst().toIndex()).isEqualTo(3);
        assertThat(scenario.swings().getFirst().toPrice()).isEqualByComparingTo(series.getBar(3).getHighPrice());
    }

    @Test
    void logicProfileAppliesDefaultBaseConfidenceWeightWhenNotOverridden() throws Exception {
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .logicProfile(ElliottLogicProfile.ANCHOR_FIRST_HYBRID)
                .build();

        Field logicProfileField = ElliottWaveAnalysisRunner.class.getDeclaredField("logicProfile");
        logicProfileField.setAccessible(true);
        Field higherDegreesField = ElliottWaveAnalysisRunner.class.getDeclaredField("higherDegrees");
        higherDegreesField.setAccessible(true);
        Field lowerDegreesField = ElliottWaveAnalysisRunner.class.getDeclaredField("lowerDegrees");
        lowerDegreesField.setAccessible(true);
        Field maxScenariosField = ElliottWaveAnalysisRunner.class.getDeclaredField("maxScenarios");
        maxScenariosField.setAccessible(true);
        Field scenarioSwingWindowField = ElliottWaveAnalysisRunner.class.getDeclaredField("scenarioSwingWindow");
        scenarioSwingWindowField.setAccessible(true);
        Field baseConfidenceWeightField = ElliottWaveAnalysisRunner.class.getDeclaredField("baseConfidenceWeight");
        baseConfidenceWeightField.setAccessible(true);

        assertThat(logicProfileField.get(analysis)).isEqualTo(ElliottLogicProfile.ANCHOR_FIRST_HYBRID);
        assertThat(higherDegreesField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ANCHOR_FIRST_HYBRID.higherDegrees());
        assertThat(lowerDegreesField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ANCHOR_FIRST_HYBRID.lowerDegrees());
        assertThat(maxScenariosField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ANCHOR_FIRST_HYBRID.maxScenarios());
        assertThat(scenarioSwingWindowField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ANCHOR_FIRST_HYBRID.scenarioSwingWindow());
        assertThat(baseConfidenceWeightField.getDouble(analysis))
                .isEqualTo(ElliottLogicProfile.ANCHOR_FIRST_HYBRID.baseConfidenceWeight());
    }

    @Test
    void buildDefaultsToOrthodoxClassicalLogicProfile() throws Exception {
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder().degree(ElliottDegree.PRIMARY).build();

        Field logicProfileField = ElliottWaveAnalysisRunner.class.getDeclaredField("logicProfile");
        logicProfileField.setAccessible(true);
        Field higherDegreesField = ElliottWaveAnalysisRunner.class.getDeclaredField("higherDegrees");
        higherDegreesField.setAccessible(true);
        Field lowerDegreesField = ElliottWaveAnalysisRunner.class.getDeclaredField("lowerDegrees");
        lowerDegreesField.setAccessible(true);
        Field maxScenariosField = ElliottWaveAnalysisRunner.class.getDeclaredField("maxScenarios");
        maxScenariosField.setAccessible(true);
        Field scenarioSwingWindowField = ElliottWaveAnalysisRunner.class.getDeclaredField("scenarioSwingWindow");
        scenarioSwingWindowField.setAccessible(true);
        Field baseConfidenceWeightField = ElliottWaveAnalysisRunner.class.getDeclaredField("baseConfidenceWeight");
        baseConfidenceWeightField.setAccessible(true);

        assertThat(logicProfileField.get(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL);
        assertThat(higherDegreesField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.higherDegrees());
        assertThat(lowerDegreesField.getInt(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.lowerDegrees());
        assertThat(maxScenariosField.getInt(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.maxScenarios());
        assertThat(scenarioSwingWindowField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.scenarioSwingWindow());
        assertThat(baseConfidenceWeightField.getDouble(analysis))
                .isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.baseConfidenceWeight());
    }

    @Test
    void windowScenarioAssessmentExposesDemoCompatibleAnchoredFitMetrics() {
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottScenario scenario = scenario(factory, "window-fit", ElliottPhase.WAVE5, 0.8,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(142), ElliottDegree.PRIMARY),
                        new ElliottSwing(6, 8, factory.numOf(142), factory.numOf(126), ElliottDegree.PRIMARY),
                        new ElliottSwing(8, 10, factory.numOf(126), factory.numOf(156), ElliottDegree.PRIMARY)),
                factory.numOf(92), 0.9);
        ElliottWaveAnalysisResult.BaseScenarioAssessment baseAssessment = new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                scenario, 0.82, 0.78, 0.80, List.of());
        ElliottWaveAnalysisResult.WindowScenarioAssessment assessment = new ElliottWaveAnalysisResult.WindowScenarioAssessment(
                baseAssessment, 0.76, 0.88, 0.74, 0.66);

        assertThat(assessment.structureScore()).isCloseTo(0.824, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(assessment.ruleScore()).isCloseTo(0.7533333333333333, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(assessment.spacingScore()).isCloseTo(0.8733333333333334,
                org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(assessment.strengthScore()).isCloseTo(0.825, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(assessment.fitScore()).isCloseTo(0.795, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(assessment.startAlignmentScore(0, 10)).isEqualTo(1.0);
        assertThat(assessment.endAlignmentScore(0, 10)).isEqualTo(1.0);
        assertThat(assessment.passesAnchoredWindowAcceptance(0, 10, 0.79, 0.30, 0.35, 0.80, 3)).isTrue();
        assertThat(assessment.passesAnchoredWindowAcceptance(0, 10, 0.80, 0.30, 0.35, 0.80, 3)).isFalse();
    }

    @Test
    void selectAcceptedOrFallbackBaseScenarioForWindowSkipsRejectedLeader() {
        BarSeries series = buildAnchoredWindowSelectionSeries();
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottScenario rejectedLeader = scenario(factory, "rejected-leader", ElliottPhase.WAVE5, 0.05,
                anchoredWindowSwings(factory), factory.numOf(92), 0.05);
        ElliottScenario acceptedFollower = scenario(factory, "accepted-follower", ElliottPhase.WAVE5, 0.80,
                anchoredWindowSwings(factory), factory.numOf(92), 0.90);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(rejectedLeader, acceptedFollower),
                series.getEndIndex());
        ElliottAnalysisResult analysisResult = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                acceptedFollower.swings(), acceptedFollower.swings(), scenarios, Map.of(), null, scenarios.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();
        ElliottWaveAnalysisResult result = analysis.analyze(series);
        List<ElliottWaveAnalysisResult.WindowScenarioAssessment> ranked = result.rankedBaseScenariosForWindow(series, 0,
                10, ScenarioType.IMPULSE, ElliottPhase.WAVE5, 5, Boolean.TRUE, 3);
        assertThat(ranked).hasSize(2);
        double acceptanceThreshold = (ranked.getFirst().fitScore() + ranked.get(1).fitScore()) / 2.0;

        Optional<ElliottWaveAnalysisRunner.AnchoredWindowSelection> selected = analysis
                .selectAcceptedOrFallbackBaseScenarioForWindow(series, 0, 10, ScenarioType.IMPULSE, ElliottPhase.WAVE5,
                        5, Boolean.TRUE, 3, acceptanceThreshold, 0.30, 0.35, 0.80);

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().accepted()).isTrue();
        assertThat(selected.orElseThrow().assessment().scenario().id()).isEqualTo("accepted-follower");
    }

    @Test
    void selectAcceptedOrFallbackBaseScenarioForWindowReturnsHighestFitFallback() {
        BarSeries series = buildAnchoredWindowSelectionSeries();
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottScenario rankedLeader = scenario(factory, "ranked-leader", ElliottPhase.WAVE5, 0.10,
                anchoredWindowSwings(factory), factory.numOf(92), 0.05);
        ElliottScenario strongerFallback = scenario(factory, "stronger-fallback", ElliottPhase.WAVE5, 0.90,
                anchoredWindowSwings(factory), factory.numOf(92), 0.95);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(rankedLeader, strongerFallback),
                series.getEndIndex());
        ElliottAnalysisResult analysisResult = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                strongerFallback.swings(), strongerFallback.swings(), scenarios, Map.of(), null, scenarios.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();
        ElliottWaveAnalysisResult result = analysis.analyze(series);
        List<ElliottWaveAnalysisResult.WindowScenarioAssessment> ranked = result.rankedBaseScenariosForWindow(series, 0,
                10, ScenarioType.IMPULSE, ElliottPhase.WAVE5, 5, Boolean.TRUE, 3);
        assertThat(ranked).hasSize(2);

        Optional<ElliottWaveAnalysisRunner.AnchoredWindowSelection> selected = analysis
                .selectAcceptedOrFallbackBaseScenarioForWindow(series, 0, 10, ScenarioType.IMPULSE, ElliottPhase.WAVE5,
                        5, Boolean.TRUE, 3, 0.95, 0.95, 0.95, 0.95);

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().accepted()).isFalse();
        assertThat(selected.orElseThrow().assessment().scenario().id()).isEqualTo("stronger-fallback");
    }

    @Test
    void selectAcceptedOrFallbackTerminalLegForWindowMatchesBullishTerminalSelection() {
        BarSeries series = buildAnchoredWindowSelectionSeries();
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottScenario rejectedLeader = scenario(factory, "rejected-leader", ElliottPhase.WAVE5, 0.05,
                anchoredWindowSwings(factory), factory.numOf(92), 0.05);
        ElliottScenario acceptedFollower = scenario(factory, "accepted-follower", ElliottPhase.WAVE5, 0.80,
                anchoredWindowSwings(factory), factory.numOf(92), 0.90);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(rejectedLeader, acceptedFollower),
                series.getEndIndex());
        ElliottAnalysisResult analysisResult = new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(),
                acceptedFollower.swings(), acceptedFollower.swings(), scenarios, Map.of(), null, scenarios.trendBias());
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();
        ElliottWaveAnalysisResult result = analysis.analyze(series);
        List<ElliottWaveAnalysisResult.WindowScenarioAssessment> ranked = result.rankedBaseScenariosForWindow(series, 0,
                10, ScenarioType.IMPULSE, ElliottPhase.WAVE5, 5, Boolean.TRUE, 3);
        double acceptanceThreshold = (ranked.getFirst().fitScore() + ranked.get(1).fitScore()) / 2.0;

        Optional<ElliottWaveAnalysisRunner.AnchoredWindowSelection> selected = analysis
                .selectAcceptedOrFallbackTerminalLegForWindow(series, 0, 10, true, 3, acceptanceThreshold, 0.30, 0.35,
                        0.80);

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().accepted()).isTrue();
        assertThat(selected.orElseThrow().assessment().scenario().id()).isEqualTo("accepted-follower");
    }

    @Test
    void selectAcceptedOrFallbackTerminalLegForWindowMatchesBearishTerminalSelection() {
        BarSeries series = buildBearishWindowSeries();
        NumFactory factory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        ElliottAnalysisResult analysisResult = bearishCorrectiveSnapshot(series, factory);
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner((ignoredSeries, ignoredDegree) -> analysisResult)
                .build();

        Optional<ElliottWaveAnalysisRunner.AnchoredWindowSelection> selected = analysis
                .selectAcceptedOrFallbackTerminalLegForWindow(series, 0, 5, false, 3, 0.30, 0.30, 0.30, 0.30);

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().accepted()).isTrue();
        assertThat(selected.orElseThrow().assessment().scenario().id()).isEqualTo("bearish-corrective-c");
        assertThat(selected.orElseThrow().assessment().scenario().currentPhase()).isEqualTo(ElliottPhase.CORRECTIVE_C);
    }

    @Test
    void buildRequiresDegree() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ElliottWaveAnalysisRunner.builder().build());
        assertThat(exception).hasMessage("degree must be configured");
    }

    @Test
    void rejectsInvalidConfidenceThresholds() {
        IllegalArgumentException low = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().minConfidence(-0.01));
        assertThat(low).hasMessage("minConfidence must be in [0.0, 1.0]");

        IllegalArgumentException high = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().minConfidence(1.01));
        assertThat(high).hasMessage("minConfidence must be in [0.0, 1.0]");
    }

    @Test
    void rejectsInvalidScenarioParameters() {
        IllegalArgumentException maxScenarios = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().maxScenarios(0));
        assertThat(maxScenarios).hasMessage("maxScenarios must be positive");

        IllegalArgumentException window = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().scenarioSwingWindow(-1));
        assertThat(window).hasMessage("scenarioSwingWindow must be >= 0");
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("AnalyzerTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 8; i++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(i)))
                    .openPrice(100 + i)
                    .highPrice(120 + i)
                    .lowPrice(90 + i)
                    .closePrice(110 + i)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildTerminalExtensionSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("TerminalExtension").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-02-01T00:00:00Z");
        double[][] bars = { { 100, 104, 98, 103 }, { 103, 108, 101, 107 }, { 107, 120, 106, 118 },
                { 118, 119, 111, 112 }, { 112, 114, 108, 110 }, { 110, 132, 109, 128 }, { 128, 144, 124, 140 },
                { 140, 141, 126, 129 }, { 129, 130, 126, 127 }, { 127, 152, 126, 149 }, { 149, 160, 148, 158 } };
        for (int index = 0; index < bars.length; index++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bars[index][0])
                    .highPrice(bars[index][1])
                    .lowPrice(bars[index][2])
                    .closePrice(bars[index][3])
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private BarSeries buildLongHistorySeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("LongHistory").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2023-01-01T00:00:00Z");
        for (int index = 0; index < 300; index++) {
            double close = 120 + (index * 0.25);
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close - 1.0)
                    .highPrice(close + 2.0)
                    .lowPrice(close - 3.0)
                    .closePrice(close)
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private BarSeries buildCurrentCycleStartPressureSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("CurrentCycleStartPressure").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2023-01-01T00:00:00Z");
        for (int index = 0; index < 300; index++) {
            double base = 220 + (index * 2.0);
            double low = base - 18.0;
            if (index == 40) {
                low = 100.0;
            } else if (index >= 90 && (index - 90) % 13 == 0 && index <= 285) {
                low = 101.0 + ((index - 90) / 13.0);
            }
            double high = index == 299 ? 1000.0 : base + 24.0;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(base - 4.0)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(base + 3.0)
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private BarSeries buildHistoricalMacroBottomSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("HistoricalMacroBottom").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2021-01-01T00:00:00Z");
        double[] closes = { 220, 180, 100, 140, 180, 230, 270, 290, 300, 260, 220, 190, 175, 165, 150, 190, 220, 250,
                280, 240, 200, 240, 280, 320, 360 };
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private static ElliottScenario scenario(final NumFactory factory, final String id, final ElliottPhase phase,
            final double overallScore, final List<ElliottSwing> swings, final org.ta4j.core.num.Num invalidationPrice,
            final double completenessScore) {
        return scenario(factory, id, phase, overallScore, swings, invalidationPrice, completenessScore,
                ScenarioType.IMPULSE);
    }

    private static ElliottScenario scenario(final NumFactory factory, final String id, final ElliottPhase phase,
            final double overallScore, final List<ElliottSwing> swings, final org.ta4j.core.num.Num invalidationPrice,
            final double completenessScore, final ScenarioType type) {
        return scenario(factory, id, phase, overallScore, swings, invalidationPrice, completenessScore,
                ElliottDegree.PRIMARY, type);
    }

    private static ElliottScenario scenario(final NumFactory factory, final String id, final ElliottPhase phase,
            final double overallScore, final List<ElliottSwing> swings, final org.ta4j.core.num.Num invalidationPrice,
            final double completenessScore, final ElliottDegree degree, final ScenarioType type) {
        ElliottConfidence confidence = new ElliottConfidence(factory.numOf(overallScore), factory.numOf(overallScore),
                factory.numOf(overallScore), factory.numOf(overallScore), factory.numOf(overallScore),
                factory.numOf(completenessScore), "test");

        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .swings(swings)
                .confidence(confidence)
                .degree(degree)
                .invalidationPrice(invalidationPrice)
                .type(type)
                .build();
    }

    private ElliottWaveAnalysisResult.CurrentPhaseAssessment currentPhaseAssessment(final NumFactory factory,
            final String scenarioId, final ElliottPhase phase, final double fitScore, final String countLabel) {
        ElliottScenario scenario = scenario(factory, scenarioId, phase, fitScore,
                List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                        new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                        new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(132), ElliottDegree.PRIMARY)),
                factory.numOf(100), fitScore);
        return new ElliottWaveAnalysisResult.CurrentPhaseAssessment(scenario, phase, fitScore, factory.hundred(),
                countLabel, factory.numOf(100), factory.numOf(108));
    }

    private ElliottWaveAnalysisRunner.AnchoredWindowSelection historicalAnchoredSelection(final NumFactory factory,
            final String scenarioId, final ElliottPhase phase, final boolean bullish, final boolean accepted,
            final double fitScore) {
        ElliottScenario scenario = bullish
                ? scenario(factory, scenarioId, phase, fitScore, anchoredWindowSwings(factory), factory.numOf(92), 0.9)
                : scenario(factory, scenarioId, phase, fitScore,
                        List.of(new ElliottSwing(0, 2, factory.numOf(160), factory.numOf(130), ElliottDegree.PRIMARY),
                                new ElliottSwing(2, 4, factory.numOf(130), factory.numOf(145), ElliottDegree.PRIMARY),
                                new ElliottSwing(4, 6, factory.numOf(145), factory.numOf(120), ElliottDegree.PRIMARY)),
                        factory.numOf(160), 0.9);
        ElliottWaveAnalysisResult.BaseScenarioAssessment baseAssessment = new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                scenario, fitScore, fitScore, fitScore, List.of());
        ElliottWaveAnalysisResult.WindowScenarioAssessment assessment = new ElliottWaveAnalysisResult.WindowScenarioAssessment(
                baseAssessment, fitScore, fitScore, fitScore, fitScore);
        return new ElliottWaveAnalysisRunner.AnchoredWindowSelection(assessment, accepted);
    }

    private List<ElliottSwing> anchoredWindowSwings(final NumFactory factory) {
        return List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 6, factory.numOf(108), factory.numOf(142), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 8, factory.numOf(142), factory.numOf(126), ElliottDegree.PRIMARY),
                new ElliottSwing(8, 10, factory.numOf(126), factory.numOf(156), ElliottDegree.PRIMARY));
    }

    private BarSeries buildAnchoredWindowSelectionSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("AnchoredWindowSelection").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-03-01T00:00:00Z");
        double[][] bars = { { 100, 103, 100, 102 }, { 102, 112, 101, 110 }, { 110, 120, 108, 118 },
                { 118, 119, 111, 114 }, { 114, 116, 108, 109 }, { 109, 130, 109, 128 }, { 128, 142, 126, 140 },
                { 140, 141, 128, 130 }, { 130, 132, 126, 128 }, { 128, 145, 127, 142 }, { 142, 156, 132, 154 } };
        for (int index = 0; index < bars.length; index++) {
            double[] bar = bars[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bar[0])
                    .highPrice(bar[1])
                    .lowPrice(bar[2])
                    .closePrice(bar[3])
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildBearishWindowSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("BearishWindow").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-04-01T00:00:00Z");
        double[][] bars = { { 200, 205, 198, 202 }, { 202, 203, 180, 184 }, { 184, 186, 150, 154 },
                { 154, 168, 152, 166 }, { 166, 176, 164, 172 }, { 172, 173, 132, 138 }, { 138, 140, 120, 124 } };
        for (int index = 0; index < bars.length; index++) {
            double[] bar = bars[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bar[0])
                    .highPrice(bar[1])
                    .lowPrice(bar[2])
                    .closePrice(bar[3])
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildDenseMacroPivotSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("DenseMacroPivot").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-05-01T00:00:00Z");
        double[] pivots = denseMacroPivotPrices();
        for (int index = 0; index < pivots.length; index++) {
            double pivotPrice = pivots[index];
            boolean highPivot = index % 2 == 1;
            double high = highPivot ? pivotPrice : pivotPrice + 2.0;
            double low = highPivot ? pivotPrice - 2.0 : pivotPrice;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(pivotPrice)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(pivotPrice)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private ElliottAnalysisResult currentCycleSnapshot(final BarSeries series, final NumFactory factory) {
        if (series.getBarCount() < 10) {
            ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
            return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), List.of(), List.of(), empty,
                    Map.of(), null, empty.trendBias());
        }

        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 4, factory.numOf(100), factory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 7, factory.numOf(180), factory.numOf(140), ElliottDegree.PRIMARY),
                new ElliottSwing(7, 9, factory.numOf(140), factory.numOf(210), ElliottDegree.PRIMARY));
        ElliottScenario wave3 = scenario(factory, "current-wave-3", ElliottPhase.WAVE3, 0.86, swings,
                factory.numOf(100), 0.82);
        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(wave3), series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings, swings, scenarios,
                Map.of(), null, scenarios.trendBias());
    }

    private ElliottAnalysisResult currentCycleStartPressureSnapshot(final BarSeries series, final NumFactory factory) {
        List<Integer> lowPivots = List.of(40, 90, 103, 116, 129, 142, 155, 168, 181, 194, 207, 220, 233, 246, 259, 272,
                285);
        List<ElliottSwing> swings = new ArrayList<>();
        for (int index = 0; index < lowPivots.size(); index++) {
            int lowIndex = lowPivots.get(index);
            int highIndex = Math.min(series.getEndIndex(), lowIndex + 6);
            double lowPrice = 100.0 + index;
            double highPrice = 180.0 + (index * 20.0);
            swings.add(new ElliottSwing(lowIndex, highIndex, factory.numOf(lowPrice), factory.numOf(highPrice),
                    ElliottDegree.PRIMARY));
            if (index + 1 < lowPivots.size()) {
                int nextLowIndex = lowPivots.get(index + 1);
                double nextLowPrice = 101.0 + index;
                swings.add(new ElliottSwing(highIndex, nextLowIndex, factory.numOf(highPrice),
                        factory.numOf(nextLowPrice), ElliottDegree.PRIMARY));
            }
        }
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings, swings, empty, Map.of(),
                null, empty.trendBias());
    }

    private ElliottAnalysisResult malformedWaveFiveSnapshot(final BarSeries series, final NumFactory factory) {
        if (series.getBarCount() < 10) {
            ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
            return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), List.of(), List.of(), empty,
                    Map.of(), null, empty.trendBias());
        }

        List<ElliottSwing> waveFourSwings = List.of(
                new ElliottSwing(0, 2, factory.numOf(90), factory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, factory.numOf(180), factory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 7, factory.numOf(120), factory.numOf(250), ElliottDegree.PRIMARY),
                new ElliottSwing(7, 9, factory.numOf(250), factory.numOf(130), ElliottDegree.PRIMARY));
        ElliottScenario waveFour = scenario(factory, "current-wave-4", ElliottPhase.WAVE4, 0.86, waveFourSwings,
                factory.numOf(90), 0.84);

        List<ElliottSwing> malformedWaveFiveSwings = List.of(
                new ElliottSwing(0, 2, factory.numOf(90), factory.numOf(180), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, factory.numOf(180), factory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 7, factory.numOf(120), factory.numOf(250), ElliottDegree.PRIMARY),
                new ElliottSwing(7, 8, factory.numOf(250), factory.numOf(160), ElliottDegree.PRIMARY),
                new ElliottSwing(8, 9, factory.numOf(160), factory.numOf(170), ElliottDegree.PRIMARY));
        ElliottScenario malformedWaveFive = scenario(factory, "malformed-wave-5", ElliottPhase.WAVE5, 0.95,
                malformedWaveFiveSwings, factory.numOf(90), 0.95);

        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(malformedWaveFive, waveFour),
                series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), malformedWaveFiveSwings,
                malformedWaveFiveSwings, scenarios, Map.of(), null, scenarios.trendBias());
    }

    private ElliottAnalysisResult bearishCorrectiveSnapshot(final BarSeries series, final NumFactory factory) {
        if (series.getBarCount() < 7) {
            ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
            return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), List.of(), List.of(), empty,
                    Map.of(), null, empty.trendBias());
        }

        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 2, factory.numOf(205), factory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 3, factory.numOf(150), factory.numOf(174), ElliottDegree.PRIMARY),
                new ElliottSwing(3, 5, factory.numOf(174), factory.numOf(124), ElliottDegree.PRIMARY));
        ElliottConfidence confidence = new ElliottConfidence(factory.numOf(0.82), factory.numOf(0.82),
                factory.numOf(0.82), factory.numOf(0.82), factory.numOf(0.82), factory.numOf(0.82), "test");
        ElliottScenario corrective = ElliottScenario.builder()
                .id("bearish-corrective-c")
                .currentPhase(ElliottPhase.CORRECTIVE_C)
                .swings(swings)
                .confidence(confidence)
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(factory.numOf(205))
                .type(ScenarioType.CORRECTIVE_ZIGZAG)
                .startIndex(0)
                .bullishDirection(false)
                .build();

        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(corrective), series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings, swings, scenarios,
                Map.of(), null, scenarios.trendBias());
    }

    private ElliottAnalysisResult denseMacroPivotSnapshot(final BarSeries series, final NumFactory factory) {
        double[] pivots = denseMacroPivotPrices();
        List<ElliottSwing> swings = new ArrayList<>(pivots.length - 1);
        for (int index = 0; index < pivots.length - 1; index++) {
            swings.add(new ElliottSwing(index, index + 1, factory.numOf(pivots[index]),
                    factory.numOf(pivots[index + 1]), ElliottDegree.PRIMARY));
        }
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings, swings, empty, Map.of(),
                null, empty.trendBias());
    }

    private double[] denseMacroPivotPrices() {
        return new double[] { 100.0, 101.0, 100.4, 101.2, 100.7, 101.4, 100.9, 102.0, 130.0, 118.0, 119.5, 117.8, 120.2,
                118.4, 119.8, 117.2, 80.0, 92.0, 91.0, 93.0, 92.2, 94.0, 93.1, 96.0, 150.0, 135.0, 136.0, 133.0,
                110.0 };
    }

    private BarSeries buildBucketCoverageMacroPivotSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("BucketCoverageMacroPivot").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-07-01T00:00:00Z");
        double[] pivots = bucketCoverageMacroPivotPrices();
        for (int index = 0; index < pivots.length; index++) {
            double pivotPrice = pivots[index];
            boolean highPivot = index % 2 == 1;
            double high = highPivot ? pivotPrice : pivotPrice + 2.0;
            double low = highPivot ? pivotPrice - 2.0 : pivotPrice;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(pivotPrice)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(pivotPrice)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private ElliottAnalysisResult bucketCoverageMacroPivotSnapshot(final BarSeries series, final NumFactory factory) {
        double[] pivots = bucketCoverageMacroPivotPrices();
        List<ElliottSwing> swings = new ArrayList<>(pivots.length - 1);
        for (int index = 0; index < pivots.length - 1; index++) {
            swings.add(new ElliottSwing(index, index + 1, factory.numOf(pivots[index]),
                    factory.numOf(pivots[index + 1]), ElliottDegree.PRIMARY));
        }
        ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), swings, swings, empty, Map.of(),
                null, empty.trendBias());
    }

    private double[] bucketCoverageMacroPivotPrices() {
        return new double[] { 100.0, 105.0, 101.0, 106.0, 102.0, 107.0, 103.0, 108.0, 104.0, 109.0, 105.0, 110.0, 106.0,
                111.0, 107.0, 112.0, 108.0, 180.0, 120.0, 220.0, 125.0, 260.0, 130.0, 300.0, 135.0, 340.0, 140.0, 380.0,
                145.0, 420.0, 150.0, 460.0, 155.0 };
    }

    private BarSeries buildMergedRawMacroPivotSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("MergedRawMacroPivot").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-08-01T00:00:00Z");
        double[] closes = { 100, 112, 125, 138, 150, 132, 114, 96, 80, 94, 108, 120, 130, 120, 110, 101, 95, 112, 130,
                145, 160, 148, 136, 122, 110, 126, 144, 162, 180 };
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close)
                    .highPrice(close + 2.0)
                    .lowPrice(close - 2.0)
                    .closePrice(close)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildCollapsedNormalizationMacroPivotSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("CollapsedNormalizationMacroPivot").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2021-01-01T00:00:00Z");
        double[] closes = new double[25];
        int[] pivotIndexes = { 0, 8, 14, 16, 18, 24 };
        double[] pivotCloses = { 100, 180, 70, 150, 60, 190 };
        for (int segment = 0; segment < pivotIndexes.length - 1; segment++) {
            int start = pivotIndexes[segment];
            int end = pivotIndexes[segment + 1];
            double startClose = pivotCloses[segment];
            double endClose = pivotCloses[segment + 1];
            for (int index = start; index <= end; index++) {
                double progress = (index - start) / (double) Math.max(1, end - start);
                closes[index] = startClose + ((endClose - startClose) * progress);
            }
        }
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close)
                    .highPrice(close + 2.0)
                    .lowPrice(close - 2.0)
                    .closePrice(close)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildBucketedDominanceMacroPivotSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("BucketedDominanceMacroPivot").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2020-01-01T00:00:00Z");
        double[] closes = new double[80];
        int[] pivotIndexes = { 0, 8, 14, 16, 18, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 72, 76, 79 };
        double[] pivotCloses = { 100, 180, 62, 176, 60, 100, 72, 120, 78, 130, 84, 140, 90, 150, 96, 160, 102, 170, 108,
                118 };
        for (int segment = 0; segment < pivotIndexes.length - 1; segment++) {
            int start = pivotIndexes[segment];
            int end = pivotIndexes[segment + 1];
            double startClose = pivotCloses[segment];
            double endClose = pivotCloses[segment + 1];
            for (int index = start; index <= end; index++) {
                double progress = (index - start) / (double) Math.max(1, end - start);
                closes[index] = startClose + ((endClose - startClose) * progress);
            }
        }
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close)
                    .highPrice(close + 2.0)
                    .lowPrice(close - 2.0)
                    .closePrice(close)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildPostHighBucketMacroPivotSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("PostHighBucketMacroPivot").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2022-01-01T00:00:00Z");
        double[] closes = new double[120];
        int[] pivotIndexes = { 0, 4, 6, 8, 14, 18, 26, 34, 42, 50, 58, 66, 74, 82, 90, 98, 106, 114, 119 };
        double[] pivotCloses = { 100, 160, 50, 180, 70, 120, 80, 130, 90, 140, 100, 150, 110, 160, 120, 170, 130, 180,
                140 };
        for (int segment = 0; segment < pivotIndexes.length - 1; segment++) {
            int start = pivotIndexes[segment];
            int end = pivotIndexes[segment + 1];
            double startClose = pivotCloses[segment];
            double endClose = pivotCloses[segment + 1];
            for (int index = start; index <= end; index++) {
                double progress = (index - start) / (double) Math.max(1, end - start);
                closes[index] = startClose + ((endClose - startClose) * progress);
            }
        }
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(close)
                    .highPrice(close + 2.0)
                    .lowPrice(close - 2.0)
                    .closePrice(close)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private BarSeries buildNonAdjacentHistoricalCycleSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("NonAdjacentHistoricalCycle").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-09-01T00:00:00Z");
        double[] pivots = nonAdjacentHistoricalCyclePrices();
        for (int index = 0; index < pivots.length; index++) {
            double pivotPrice = pivots[index];
            boolean highPivot = index % 2 == 1;
            double high = highPivot ? pivotPrice : pivotPrice + 2.0;
            double low = highPivot ? pivotPrice - 2.0 : pivotPrice;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(pivotPrice)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(pivotPrice)
                    .volume(1000)
                    .add();
        }
        return series;
    }

    private ElliottAnalysisResult nonAdjacentHistoricalCycleSnapshot(final BarSeries series, final NumFactory factory) {
        double[] pivots = nonAdjacentHistoricalCyclePrices();
        List<ElliottSwing> processedSwings = new ArrayList<>(pivots.length - 1);
        for (int index = 0; index < pivots.length - 1; index++) {
            processedSwings.add(new ElliottSwing(index, index + 1, factory.numOf(pivots[index]),
                    factory.numOf(pivots[index + 1]), ElliottDegree.PRIMARY));
        }

        ElliottScenarioSet scenarios = ElliottScenarioSet.empty(series.getEndIndex());
        if (series.getBarCount() >= 6
                && series.getLastBar().getClosePrice().isGreaterThan(series.getBar(0).getClosePrice())) {
            List<ElliottSwing> swings = List.of(
                    new ElliottSwing(0, 1, factory.numOf(100), factory.numOf(120), ElliottDegree.PRIMARY),
                    new ElliottSwing(1, 2, factory.numOf(120), factory.numOf(108), ElliottDegree.PRIMARY),
                    new ElliottSwing(2, 3, factory.numOf(108), factory.numOf(150), ElliottDegree.PRIMARY),
                    new ElliottSwing(3, 4, factory.numOf(150), factory.numOf(128), ElliottDegree.PRIMARY),
                    new ElliottSwing(4, 5, factory.numOf(128), factory.numOf(180), ElliottDegree.PRIMARY));
            ElliottScenario waveFive = scenario(factory, "history-wave-5", ElliottPhase.WAVE5, 0.88, swings,
                    factory.numOf(100), 0.88);
            scenarios = ElliottScenarioSet.of(List.of(waveFive), series.getEndIndex());
        } else if (series.getBarCount() >= 4
                && series.getLastBar().getClosePrice().isLessThan(series.getBar(0).getClosePrice())) {
            List<ElliottSwing> swings = List.of(
                    new ElliottSwing(0, 1, factory.numOf(180), factory.numOf(150), ElliottDegree.PRIMARY),
                    new ElliottSwing(1, 2, factory.numOf(150), factory.numOf(165), ElliottDegree.PRIMARY),
                    new ElliottSwing(2, 3, factory.numOf(165), factory.numOf(130), ElliottDegree.PRIMARY));
            ElliottScenario corrective = scenario(factory, "history-corrective-c", ElliottPhase.CORRECTIVE_C, 0.84,
                    swings, factory.numOf(180), 0.84);
            scenarios = ElliottScenarioSet.of(List.of(corrective), series.getEndIndex());
        }
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), processedSwings, processedSwings,
                scenarios, Map.of(), null, scenarios.trendBias());
    }

    private double[] nonAdjacentHistoricalCyclePrices() {
        return new double[] { 160.0, 130.0, 145.0, 100.0, 120.0, 108.0, 150.0, 128.0, 180.0, 150.0, 165.0, 130.0, 150.0,
                138.0, 170.0, 152.0, 190.0 };
    }

    private ElliottAnalysisResult waveFourNormalizationSnapshot(final BarSeries series, final NumFactory factory) {
        if (series.getBarCount() < 10) {
            ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
            return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), List.of(), List.of(), empty,
                    Map.of(), null, empty.trendBias());
        }

        List<ElliottSwing> waveFourSwings = List.of(
                new ElliottSwing(0, 2, factory.numOf(90), factory.numOf(140), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, factory.numOf(140), factory.numOf(108), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 7, factory.numOf(108), factory.numOf(248), ElliottDegree.PRIMARY),
                new ElliottSwing(7, 9, factory.numOf(248), factory.numOf(150), ElliottDegree.PRIMARY));
        ElliottScenario waveFour = scenario(factory, "wave-four-needs-normalization", ElliottPhase.WAVE4, 0.91,
                waveFourSwings, factory.numOf(90), 0.88);

        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(waveFour), series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), waveFourSwings, waveFourSwings,
                scenarios, Map.of(), null, scenarios.trendBias());
    }

    private ElliottAnalysisResult waveTwoNormalizationSnapshot(final BarSeries series, final NumFactory factory) {
        if (series.getBarCount() < 5) {
            ElliottScenarioSet empty = ElliottScenarioSet.empty(series.getEndIndex());
            return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), List.of(), List.of(), empty,
                    Map.of(), null, empty.trendBias());
        }

        List<ElliottSwing> waveTwoSwings = List.of(
                new ElliottSwing(0, 2, factory.numOf(90), factory.numOf(138), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 4, factory.numOf(138), factory.numOf(108), ElliottDegree.PRIMARY));
        ElliottScenario waveTwo = scenario(factory, "wave-two-needs-normalization", ElliottPhase.WAVE2, 0.90,
                waveTwoSwings, factory.numOf(90), 0.84);

        ElliottScenarioSet scenarios = ElliottScenarioSet.of(List.of(waveTwo), series.getEndIndex());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), waveTwoSwings, waveTwoSwings,
                scenarios, Map.of(), null, scenarios.trendBias());
    }

    private BarSeries buildCurrentCycleSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("CurrentCycle").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-06-01T00:00:00Z");
        double[][] bars = { { 102, 110, 100, 108 }, { 108, 125, 104, 122 }, { 122, 142, 118, 138 },
                { 138, 162, 132, 156 }, { 156, 180, 150, 175 }, { 175, 176, 160, 164 }, { 164, 168, 150, 152 },
                { 152, 162, 140, 145 }, { 145, 188, 160, 182 }, { 182, 210, 190, 205 } };
        for (int index = 0; index < bars.length; index++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bars[index][0])
                    .highPrice(bars[index][1])
                    .lowPrice(bars[index][2])
                    .closePrice(bars[index][3])
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private BarSeries buildMalformedWaveFiveSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("MalformedWaveFive").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-07-01T00:00:00Z");
        double[][] bars = { { 92, 100, 90, 96 }, { 96, 150, 100, 145 }, { 145, 180, 140, 172 }, { 172, 170, 130, 138 },
                { 138, 150, 120, 128 }, { 128, 200, 150, 192 }, { 192, 230, 180, 225 }, { 225, 250, 210, 240 },
                { 240, 200, 160, 168 }, { 168, 170, 130, 160 } };
        for (int index = 0; index < bars.length; index++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bars[index][0])
                    .highPrice(bars[index][1])
                    .lowPrice(bars[index][2])
                    .closePrice(bars[index][3])
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private BarSeries buildWaveFourNormalizationSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("WaveFourNormalization").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-08-01T00:00:00Z");
        double[][] bars = { { 92, 98, 90, 96 }, { 96, 118, 95, 116 }, { 116, 140, 110, 136 }, { 136, 132, 116, 120 },
                { 120, 126, 108, 112 }, { 112, 180, 110, 172 }, { 172, 224, 164, 218 }, { 218, 248, 210, 240 },
                { 240, 262, 214, 228 }, { 228, 230, 150, 160 } };
        for (int index = 0; index < bars.length; index++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bars[index][0])
                    .highPrice(bars[index][1])
                    .lowPrice(bars[index][2])
                    .closePrice(bars[index][3])
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    private BarSeries buildWaveTwoNormalizationSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("WaveTwoNormalization").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-09-01T00:00:00Z");
        double[][] bars = { { 92, 98, 90, 96 }, { 96, 120, 94, 116 }, { 116, 138, 110, 126 }, { 126, 150, 114, 120 },
                { 120, 124, 108, 112 } };
        for (int index = 0; index < bars.length; index++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index)))
                    .openPrice(bars[index][0])
                    .highPrice(bars[index][1])
                    .lowPrice(bars[index][2])
                    .closePrice(bars[index][3])
                    .volume(1_000)
                    .add();
        }
        return series;
    }

}

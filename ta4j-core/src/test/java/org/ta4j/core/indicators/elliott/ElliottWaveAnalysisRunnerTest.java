/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
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

        assertThat(logicProfileField.get(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL);
        assertThat(higherDegreesField.getInt(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.higherDegrees());
        assertThat(lowerDegreesField.getInt(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.lowerDegrees());
        assertThat(maxScenariosField.getInt(analysis)).isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.maxScenarios());
        assertThat(scenarioSwingWindowField.getInt(analysis))
                .isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.scenarioSwingWindow());
        assertThat(baseConfidenceWeightField.getDouble(analysis))
                .isEqualTo(ElliottLogicProfile.ORTHODOX_CLASSICAL.baseConfidenceWeight());
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

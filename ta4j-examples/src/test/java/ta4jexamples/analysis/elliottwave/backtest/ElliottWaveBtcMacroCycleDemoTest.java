/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;

@Tag("integration")
@Tag("slow")
class ElliottWaveBtcMacroCycleDemoTest {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class);

    @TempDir
    Path tempDirectory;

    @Test
    void buildWaveLabelsFromScenarioUsesCurrentPhaseInsteadOfScenarioType() {
        BarSeries series = extendedSyntheticSeries();
        ElliottScenario bullishScenario = ElliottScenario.builder()
                .id("btc-bullish")
                .currentPhase(ElliottPhase.WAVE5)
                .swings(List.of(
                        new ElliottSwing(0, 1, series.numFactory().numOf(100), series.numFactory().numOf(120),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(1, 2, series.numFactory().numOf(120), series.numFactory().numOf(108),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(2, 3, series.numFactory().numOf(108), series.numFactory().numOf(146),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(3, 4, series.numFactory().numOf(146), series.numFactory().numOf(130),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(4, 5, series.numFactory().numOf(130), series.numFactory().numOf(156),
                                ElliottDegree.MINUTE)))
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.MINUTE)
                .type(ScenarioType.CORRECTIVE_ZIGZAG)
                .startIndex(0)
                .build();
        ElliottScenario correctiveScenario = ElliottScenario.builder()
                .id("btc-corrective")
                .currentPhase(ElliottPhase.CORRECTIVE_C)
                .swings(List.of(
                        new ElliottSwing(0, 1, series.numFactory().numOf(160), series.numFactory().numOf(130),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(1, 2, series.numFactory().numOf(130), series.numFactory().numOf(145),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(2, 3, series.numFactory().numOf(145), series.numFactory().numOf(118),
                                ElliottDegree.MINUTE)))
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.MINUTE)
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();

        List<BarLabel> bullishLabels = ElliottWaveMacroCycleDemo.buildWaveLabelsFromScenario(series, bullishScenario,
                ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR);
        List<BarLabel> correctiveLabels = ElliottWaveMacroCycleDemo.buildWaveLabelsFromScenario(series,
                correctiveScenario, ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR);

        assertEquals(List.of("1", "2", "3", "4", "5"), bullishLabels.stream().map(BarLabel::text).toList());
        assertEquals(List.of("A", "B", "C"), correctiveLabels.stream().map(BarLabel::text).toList());
    }

    @Test
    void fitFromCoreAssessmentUsesCoreCompositeAsPrimaryAcceptanceGate() throws Exception {
        BarSeries series = studySyntheticSeries();
        ElliottWaveMacroCycleDemo.LegSegment segment = new ElliottWaveMacroCycleDemo.LegSegment(
                anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 2),
                anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 7), true);
        ElliottScenario scenario = ElliottScenario.builder()
                .id("prefix-history-bullish")
                .currentPhase(ElliottPhase.WAVE5)
                .swings(List.of(
                        new ElliottSwing(2, 3, series.numFactory().numOf(108), series.numFactory().numOf(146),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(3, 4, series.numFactory().numOf(146), series.numFactory().numOf(130),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(4, 5, series.numFactory().numOf(130), series.numFactory().numOf(156),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(5, 6, series.numFactory().numOf(156), series.numFactory().numOf(144),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(6, 7, series.numFactory().numOf(144), series.numFactory().numOf(172),
                                ElliottDegree.MINUTE)))
                .confidence(new ElliottConfidence(series.numFactory().numOf(0.84), series.numFactory().numOf(0.78),
                        series.numFactory().numOf(0.76), series.numFactory().zero(), series.numFactory().zero(),
                        series.numFactory().numOf(1.0), "Core-ranked terminal impulse"))
                .degree(ElliottDegree.MINUTE)
                .type(ScenarioType.IMPULSE)
                .bullishDirection(true)
                .startIndex(2)
                .build();
        ElliottWaveAnalysisResult.WindowScenarioAssessment assessment = new ElliottWaveAnalysisResult.WindowScenarioAssessment(
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(scenario, 0.84, 0.79, 0.82, List.of()), 0.90, 0.96,
                0.94, 1.0);

        ElliottWaveMacroCycleDemo.SegmentScenarioFit fit = invokeFitFromCoreAssessment(segment, assessment, true, true);

        assertTrue(fit.accepted());
        assertTrue(fit.ruleScore() < 0.35);
        assertEquals("Core-ranked anchored-window impulse fit", fit.rationale());
    }

    @Test
    void fitFromCoreAssessmentDoesNotRequireDemoStrengthThresholdWhenCompositeIsHigh() throws Exception {
        BarSeries series = studySyntheticSeries();
        ElliottWaveMacroCycleDemo.LegSegment segment = new ElliottWaveMacroCycleDemo.LegSegment(
                anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 2),
                anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 7), true);
        ElliottScenario scenario = ElliottScenario.builder()
                .id("anchored-impulse")
                .currentPhase(ElliottPhase.WAVE5)
                .swings(List.of(
                        new ElliottSwing(2, 3, series.numFactory().numOf(122), series.numFactory().numOf(132),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(3, 4, series.numFactory().numOf(132), series.numFactory().numOf(126),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(4, 5, series.numFactory().numOf(126), series.numFactory().numOf(164),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(5, 6, series.numFactory().numOf(164), series.numFactory().numOf(150),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(6, 7, series.numFactory().numOf(150), series.numFactory().numOf(202),
                                ElliottDegree.MINUTE)))
                .confidence(new ElliottConfidence(series.numFactory().numOf(0.66), series.numFactory().numOf(0.72),
                        series.numFactory().numOf(0.70), series.numFactory().numOf(0.20),
                        series.numFactory().numOf(0.18), series.numFactory().numOf(0.50), "Anchored impulse candidate"))
                .degree(ElliottDegree.MINUTE)
                .type(ScenarioType.IMPULSE)
                .bullishDirection(true)
                .startIndex(2)
                .build();
        ElliottWaveAnalysisResult.WindowScenarioAssessment assessment = new ElliottWaveAnalysisResult.WindowScenarioAssessment(
                new ElliottWaveAnalysisResult.BaseScenarioAssessment(scenario, 0.28, 0.18, 0.95, List.of()), 0.96, 0.92,
                0.90, 1.0);

        ElliottWaveMacroCycleDemo.SegmentScenarioFit fit = invokeFitFromCoreAssessment(segment, assessment, true, true);

        assertTrue(fit.accepted());
        assertTrue(fit.strengthScore() < 0.55);
        assertEquals("Core-ranked anchored-window impulse fit", fit.rationale());
    }

    @Test
    void evaluateMacroStudyProducesProfileTableAndCycleSummary() {
        BarSeries series = studySyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 1),
                        anchor("btc-bottom-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 2),
                        anchor("btc-top-2013", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 5),
                        anchor("btc-bottom-2015", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 7)));

        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveAnchorCalibrationHarness
                .evaluateLegacyAnchoredHistoricalStudy(series, registry);

        assertEquals(5, study.profileScores().size());
        assertEquals(1, study.cycles().size());
        assertEquals("Bullish 1-2-3-4-5", study.cycles().getFirst().impulseLabel());
        assertEquals("Bearish A-B-C", study.cycles().getFirst().correctionLabel());
        assertEquals(4, study.hypotheses().size());
        assertFalse(study.selectedProfile().profile().id().isBlank());
    }

    @Test
    void saveMacroCycleChartWritesImage() throws Exception {
        BarSeries series = chartSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 0),
                        anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 4),
                        anchor("btc-low", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 6)));
        Path tempDir = newTempDirectory("btc-macro-cycle-demo");

        Optional<Path> savedPath = ElliottWaveBtcMacroCycleDemo.saveMacroCycleChart(series, registry, tempDir);

        assertTrue(savedPath.isPresent());
        assertTrue(Files.exists(savedPath.get()));
        assertTrue(savedPath.get().getFileName().toString().endsWith(".jpg"));
        BufferedImage image = readImage(savedPath.get());
        assertEquals(ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, image.getWidth());
        assertEquals(ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT, image.getHeight());
    }

    @Test
    void realDatasetHistoricalCyclesProduceAcceptedFitsAndPersistArtifacts() throws Exception {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        Path tempDir = newTempDirectory("btc-macro-cycle-real");

        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveBtcMacroCycleDemo.evaluateMacroStudy(series, registry);
        ElliottWaveBtcMacroCycleDemo.DemoReport report = ElliottWaveBtcMacroCycleDemo.generateReport(tempDir);

        assertEquals(3, study.cycles().size());
        assertEquals(3, study.selectedProfile().cycleFits().size());
        assertTruthTargetCycleCoverage(series, study.selectedProfile().cycleFits(), registry);
        List<String> cycleSegmentKeys = study.selectedProfile()
                .cycleFits()
                .stream()
                .flatMap(cycleFit -> java.util.stream.Stream.of(cycleFit.cycle().bullishLeg(),
                        cycleFit.cycle().bearishLeg()))
                .map(segment -> segment.fromAnchor().id() + "->" + segment.toAnchor().id())
                .toList();
        List<String> chartSegmentKeys = study.selectedProfile()
                .chartSegments()
                .stream()
                .map(segment -> segment.segment().fromAnchor().id() + "->" + segment.segment().toAnchor().id())
                .toList();
        assertEquals(cycleSegmentKeys, chartSegmentKeys);
        assertTrue(study.selectedProfile()
                .chartSegments()
                .stream()
                .allMatch(segment -> segment.rationale().startsWith("Core-ranked anchored-window")));
        Optional<ElliottWaveMacroCycleDemo.SegmentScenarioFit> coreBullSegment = study.selectedProfile()
                .chartSegments()
                .stream()
                .filter(segment -> segment.segment().bullish())
                .filter(segment -> segment.segment().fromAnchor().id().equals("btc-2015-cycle-bottom"))
                .filter(segment -> segment.segment().toAnchor().id().equals("btc-2017-cycle-top"))
                .findFirst();
        assertTrue(coreBullSegment.isPresent());
        assertEquals("Core-ranked anchored-window impulse fit", coreBullSegment.orElseThrow().rationale());
        assertTrue(Files.exists(Path.of(report.chartPath())));
        assertTrue(Files.exists(Path.of(report.summaryPath())));
    }

    @Test
    void committedBitcoinTruthTargetRegistryMatchesExpectedAnchorWindows() {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);

        List<ExpectedTruthAnchor> expectedAnchors = expectedTruthAnchors();

        assertEquals("btc-macro-cycle-anchors-v2", registry.version());
        assertEquals(expectedAnchors.size(), registry.anchors().size());
        for (int index = 0; index < expectedAnchors.size(); index++) {
            ExpectedTruthAnchor expected = expectedAnchors.get(index);
            ElliottWaveAnchorCalibrationHarness.Anchor actual = registry.anchors().get(index);

            assertEquals(expected.id(), actual.id());
            assertEquals(expected.type(), actual.type());
            assertEquals(expected.partition(), actual.partition());
            assertEquals(Set.of(expected.expectedPhase()), actual.expectedPhases());
            assertEquals(expected.windowStart(), actual.at().minus(actual.toleranceBefore()));
            assertEquals(expected.windowEnd(), actual.at().plus(actual.toleranceAfter()));
            assertFalse(actual.provenance().isBlank());
        }
    }

    @Test
    void registryBackedHistoricalMacroStudyMatchesCommittedTruthTargetWithinTolerance() {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveMacroCycleDemo.evaluateMacroStudy(series, registry);

        List<ExpectedTruthCycle> expectedCycles = expectedTruthCycles();
        assertTrue(study.selectedProfile().historicalFitPassed(),
                () -> "profile=" + study.selectedProfile().profile().id() + " acceptedCycles="
                        + study.selectedProfile().acceptedCycles() + " cycleFits="
                        + study.selectedProfile().cycleFits().size() + " matchedExpected="
                        + study.selectedProfile().matchedExpectedCycles() + " missingExpected="
                        + study.selectedProfile().missingExpectedCycles() + " unexpected="
                        + study.selectedProfile().unexpectedCycles() + " missingIds="
                        + study.selectedProfile().truthTargetCoverage().missingExpectedCycleIds() + " unexpectedIds="
                        + study.selectedProfile().truthTargetCoverage().unexpectedCycleIds() + " profileScores="
                        + study.profileScores());
        assertEquals(expectedCycles.size(), study.selectedProfile().truthTargetCoverage().expectedCycleCount());
        assertEquals(expectedCycles.size(), study.selectedProfile().matchedExpectedCycles());
        assertTrue(study.selectedProfile().truthTargetCoverage().missingExpectedCycleIds().isEmpty(),
                () -> "Missing expected cycles: "
                        + study.selectedProfile().truthTargetCoverage().missingExpectedCycleIds());
        assertTrue(study.selectedProfile().truthTargetCoverage().unexpectedCycleIds().isEmpty(),
                () -> "Unexpected cycles: " + study.selectedProfile().truthTargetCoverage().unexpectedCycleIds());
        assertEquals(expectedCycles.size(), study.selectedProfile().cycleFits().size());
        assertTruthTargetCycleOrdering(study.selectedProfile().cycleFits(), expectedCycles);
        assertTruthTargetCycleCoverage(series, study.selectedProfile().cycleFits(), registry);
    }

    @Test
    void registryBackedCanonicalStructureKeepsSeriesNativeRuntimeCurrentCycleSelection() {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);

        ElliottWaveMacroCycleDemo.MacroStudy runtimeStudy = ElliottWaveMacroCycleDemo
                .evaluateCanonicalMacroStudy(series);
        ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(series, registry);

        assertTrue(structure.historicalStudy().isPresent());
        assertEquals(runtimeStudy.currentCycle().winningProfileId(),
                structure.currentCycle().summary().winningProfileId());
        assertEquals(runtimeStudy.currentCycle().primaryCount(), structure.currentCycle().summary().primaryCount());
        assertEquals(runtimeStudy.currentCycle().alternateCount(), structure.currentCycle().summary().alternateCount());
        assertEquals(runtimeStudy.currentCycle().currentWave(), structure.currentCycle().summary().currentWave());
    }

    @Test
    void legacyAnchoredHistoricalMacroStudyRemainsAvailableForComparison() {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveAnchorCalibrationHarness
                .evaluateLegacyAnchoredHistoricalStudy(series, registry);

        assertTrue(study.selectedProfile().historicalFitPassed());
        assertTrue(study.selectedProfile().cycleFits().size() >= 3);
        assertTruthTargetCycleFits(series, study.selectedProfile().cycleFits(), registry);
    }

    @Test
    void inferredHistoricalMacroStudyMatchesCommittedTruthTargetWithinTolerance() {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry inferredRegistry = ElliottWaveMacroCycleDetector
                .inferAnchorRegistry(series);
        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveAnchorCalibrationHarness
                .evaluateLegacyAnchoredHistoricalStudy(series, inferredRegistry);

        assertTrue(study.selectedProfile().historicalFitPassed());
        assertTrue(study.selectedProfile().cycleFits().size() >= 3);
        assertTruthTargetCycleFits(series, study.selectedProfile().cycleFits(), registry);
    }

    @Test
    void genericMacroCycleDemoMatchesBtcWrapperOnFullBitcoinHistory() throws Exception {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        Path wrapperDir = newTempDirectory("btc-macro-wrapper");
        Path genericDir = newTempDirectory("btc-macro-generic");

        ElliottWaveBtcMacroCycleDemo.DemoReport wrapperReport = ElliottWaveBtcMacroCycleDemo.generateReport(wrapperDir);
        ElliottWaveMacroCycleDemo.DemoReport genericReport = ElliottWaveMacroCycleDemo.generateHistoricalReport(series,
                registry, genericDir);

        assertEquals(ElliottWaveAnchorCalibrationHarness.canonicalBtcCalibratedProfile().id(),
                wrapperReport.baselineProfileId());
        assertEquals(ElliottWaveAnchorCalibrationHarness.canonicalBtcCalibratedProfile().id(),
                genericReport.baselineProfileId());
        assertEquals(wrapperReport.selectedProfileId(), genericReport.selectedProfileId());
        assertEquals(wrapperReport.selectedHypothesisId(), genericReport.selectedHypothesisId());
        assertEquals(wrapperReport.historicalFitPassed(), genericReport.historicalFitPassed());
        assertEquals("canonical-structure", wrapperReport.structureSource());
        assertEquals(wrapperReport.structureSource(), genericReport.structureSource());
        assertEquals(wrapperReport.profileScores(), genericReport.profileScores());
        assertEquals(wrapperReport.cycles(), genericReport.cycles());
        assertEquals(wrapperReport.hypotheses(), genericReport.hypotheses());
        assertTrue(Files.exists(Path.of(genericReport.chartPath())));
        assertTrue(Files.exists(Path.of(genericReport.summaryPath())));
    }

    @Test
    void genericMacroCycleDemoPersistsLiveArtifactsOnLiveWindow() throws Exception {
        BarSeries fullSeries = loadBitcoinSeries();
        BarSeries liveWindow = trailingWindow(fullSeries, 1825);
        Path wrapperDir = newTempDirectory("btc-live-wrapper");
        Path genericDir = newTempDirectory("btc-live-generic");

        ElliottWaveBtcMacroCycleDemo.LivePresetReport wrapperReport = ElliottWaveBtcMacroCycleDemo
                .generateLivePresetReport(liveWindow, wrapperDir);
        ElliottWaveMacroCycleDemo.LivePresetReport genericReport = ElliottWaveMacroCycleDemo
                .generateLivePresetReport(liveWindow, genericDir);

        assertEquals("canonical-structure", wrapperReport.structureSource());
        assertEquals(wrapperReport.structureSource(), genericReport.structureSource());
        assertTrue(Files.exists(Path.of(wrapperReport.chartPath())));
        assertTrue(Files.exists(Path.of(wrapperReport.summaryPath())));
        assertTrue(Files.exists(Path.of(genericReport.chartPath())));
        assertTrue(Files.exists(Path.of(genericReport.summaryPath())));
    }

    @Test
    void pairedCanonicalReportsReuseSingleStructureForHistoricalAndLiveViews() throws Exception {
        BarSeries series = loadBitcoinSeries();
        Path pairedDir = newTempDirectory("btc-paired-generic");

        ElliottWaveMacroCycleDemo.CanonicalReportPair pair = ElliottWaveMacroCycleDemo
                .generateCanonicalReportPair(series, pairedDir);
        ElliottWaveMacroCycleDemo.CanonicalStructure structure = pair.structure();
        ElliottWaveMacroCycleDemo.MacroStudy study = structure.historicalStudy().orElseThrow();
        ElliottWaveMacroCycleDemo.CurrentCycleSummary structureSummary = structure.currentCycle().summary();

        assertEquals(study.selectedProfile().profile().id(), pair.historicalReport().selectedProfileId());
        assertEquals(study.selectedProfile().profile().id(), pair.liveReport().selectedProfileId());
        assertEquals(study.selectedProfile().profile().hypothesisId(), pair.historicalReport().selectedHypothesisId());
        assertEquals(study.selectedProfile().profile().hypothesisId(), pair.liveReport().selectedHypothesisId());
        assertEquals(structureSummary.startTimeUtc(), pair.historicalReport().currentCycle().startTimeUtc());
        assertEquals(structureSummary.startTimeUtc(), pair.liveReport().currentCycle().startTimeUtc());
        assertEquals(structureSummary.latestTimeUtc(), pair.historicalReport().currentCycle().latestTimeUtc());
        assertEquals(structureSummary.latestTimeUtc(), pair.liveReport().currentCycle().latestTimeUtc());
        assertEquals(structureSummary.primaryCount(), pair.historicalReport().currentCycle().primaryCount());
        assertEquals(structureSummary.primaryCount(), pair.liveReport().currentCycle().primaryCount());
        assertTrue(Files.exists(Path.of(pair.historicalReport().chartPath())));
        assertTrue(Files.exists(Path.of(pair.historicalReport().summaryPath())));
        assertTrue(Files.exists(Path.of(pair.liveReport().chartPath())));
        assertTrue(Files.exists(Path.of(pair.liveReport().summaryPath())));
    }

    @Test
    void canonicalStructureCarriesHistoricalStudyWhenRegistryProvided() {
        BarSeries series = loadBitcoinSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);

        ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(series, registry);
        ElliottWaveMacroCycleDemo.MacroStudy study = structure.historicalStudy().orElseThrow();

        assertEquals(study.profileScores(), structure.historicalStudy().orElseThrow().profileScores());
        assertEquals(study.cycles(), structure.historicalStudy().orElseThrow().cycles());
        assertEquals(study.hypotheses(), structure.historicalStudy().orElseThrow().hypotheses());
    }

    @Test
    void canonicalStructureSupportsLiveOnlyAnalysisWithoutHistoricalStudy() throws Exception {
        BarSeries fullSeries = loadBitcoinSeries();
        BarSeries liveWindow = trailingWindow(fullSeries, 1825);

        ElliottWaveMacroCycleDemo.MacroLogicProfile profile = ElliottWaveMacroCycleDemo.defaultLiveMacroProfile();

        ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(liveWindow, profile, "test");

        assertTrue(structure.historicalStudy().isEmpty());
    }

    @Test
    void runLivePresetRestoresLegacyBaseCaseAndAlternativeCharts() throws Exception {
        BarSeries fullSeries = loadBitcoinSeries();
        BarSeries liveWindow = trailingWindow(fullSeries, 1825);
        Path tempDir = newTempDirectory("btc-live-preset-legacy");

        ElliottWaveBtcMacroCycleDemo.runLivePreset(liveWindow, tempDir);

        assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-base-case.jpg")));
        assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-1.jpg")));
        assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-2.jpg")));
        assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-3.jpg")));
        assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-4.jpg")));
        assertTrue(Files.exists(tempDir.resolve(ElliottWaveBtcMacroCycleDemo.DEFAULT_LIVE_SUMMARY_FILE_NAME)));
    }

    @Test
    void macroLogicProfilesStayNonPublicWhileSelectionSurfaceIsUnsettled() throws Exception {
        Method profilesMethod = ElliottWaveMacroCycleDemo.class.getDeclaredMethod("logicProfiles");
        Method defaultProfileMethod = ElliottWaveMacroCycleDemo.class.getDeclaredMethod("defaultLiveMacroProfile");

        assertFalse(Modifier.isPublic(ElliottWaveMacroCycleDemo.MacroLogicProfile.class.getModifiers()));
        assertFalse(Modifier.isPublic(profilesMethod.getModifiers()));
        assertFalse(Modifier.isPublic(defaultProfileMethod.getModifiers()));
    }

    @Test
    void renderMacroCycleChartUsesLogAxisOnMainPricePlot() {
        BarSeries series = chartSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 0),
                        anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 4),
                        anchor("btc-low", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 6)));

        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveAnchorCalibrationHarness
                .evaluateLegacyAnchoredHistoricalStudy(series, registry);
        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, study);

        assertTrue(chart.getPlot() instanceof CombinedDomainXYPlot);
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertFalse(combinedPlot.getSubplots().isEmpty());
        XYPlot mainPlot = (XYPlot) combinedPlot.getSubplots().getFirst();
        assertTrue(mainPlot.getRangeAxis() instanceof LogAxis);
        assertEquals("Price (USD, log)", mainPlot.getRangeAxis().getLabel());

        XYItemRenderer bullishRenderer = findRenderer(mainPlot, "Bullish 1-2-3-4-5");
        XYItemRenderer bearishRenderer = findRenderer(mainPlot, "Bearish A-B-C");
        assertNotNull(bullishRenderer);
        assertNotNull(bearishRenderer);
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR, bullishRenderer.getSeriesPaint(0));
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR, bearishRenderer.getSeriesPaint(0));
    }

    @Test
    void renderMacroCycleChartDrawsCompletedCycleOverlaysFromStudyCycles() {
        BarSeries series = extendedSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-1", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 0),
                        anchor("btc-bottom-1", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 1),
                        anchor("btc-top-2", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 3),
                        anchor("btc-bottom-2", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 5)));

        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveAnchorCalibrationHarness
                .evaluateLegacyAnchoredHistoricalStudy(series, registry);
        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, study);
        XYPlot mainPlot = (XYPlot) ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().getFirst();

        XYDataset bullishDataset = findDataset(mainPlot, "Bullish 1-2-3-4-5");
        XYDataset bearishDataset = findDataset(mainPlot, "Bearish A-B-C");

        assertNotNull(bullishDataset);
        assertNotNull(bearishDataset);
        assertEquals(1, bullishDataset.getSeriesCount());
        assertEquals(1, bearishDataset.getSeriesCount());
    }

    @Test
    void renderMacroCycleChartUsesProvidedStudyCycleGeometry() {
        BarSeries series = studySyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 1),
                        anchor("btc-bottom-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 2),
                        anchor("btc-top-2013", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 5),
                        anchor("btc-bottom-2015", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 7)));
        ElliottWaveMacroCycleDemo.MacroStudy study = ElliottWaveAnchorCalibrationHarness
                .evaluateLegacyAnchoredHistoricalStudy(series, registry);
        ElliottWaveMacroCycleDemo.DirectionalCycleSummary cycle = study.cycles().getFirst();
        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, study);
        XYPlot plot = (XYPlot) ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().getFirst();

        XYDataset bullishDataset = findDataset(plot, "Bullish 1-2-3-4-5");
        XYDataset bearishDataset = findDataset(plot, "Bearish A-B-C");

        assertNotNull(bullishDataset);
        assertNotNull(bearishDataset);

        int startIndex = indexOf(series, Instant.parse(cycle.startTimeUtc()));
        int peakIndex = indexOf(series, Instant.parse(cycle.peakTimeUtc()));
        int lowIndex = indexOf(series, Instant.parse(cycle.lowTimeUtc()));

        assertEquals(series.getBar(startIndex).getLowPrice().doubleValue(),
                datasetValueAtIndex(bullishDataset, startIndex), ElliottWaveBtcMacroCycleDemo.EPSILON);
        assertEquals(series.getBar(peakIndex).getHighPrice().doubleValue(),
                datasetValueAtIndex(bullishDataset, peakIndex), ElliottWaveBtcMacroCycleDemo.EPSILON);
        assertEquals(series.getBar(peakIndex).getHighPrice().doubleValue(),
                datasetValueAtIndex(bearishDataset, peakIndex), ElliottWaveBtcMacroCycleDemo.EPSILON);
        assertEquals(series.getBar(lowIndex).getLowPrice().doubleValue(), datasetValueAtIndex(bearishDataset, lowIndex),
                ElliottWaveBtcMacroCycleDemo.EPSILON);
    }

    @Test
    void interpolateOverlayPriceUsesLogSpaceOnPositivePrices() {
        double midpoint = ElliottWaveMacroCycleDemo.interpolateOverlayPrice(100.0, 1600.0, 0.5);

        assertEquals(400.0, midpoint, 1.0e-10);
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor anchor(String id,
            ElliottWaveAnchorCalibrationHarness.AnchorType type, BarSeries series, int index) {
        return new ElliottWaveAnchorCalibrationHarness.Anchor(id, type, series.getBar(index).getEndTime(),
                Duration.ZERO, Duration.ZERO,
                type == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? Set.of(ElliottPhase.WAVE5)
                        : Set.of(ElliottPhase.CORRECTIVE_C),
                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic");
    }

    private static ElliottWaveMacroCycleDemo.SegmentScenarioFit invokeFitFromCoreAssessment(
            ElliottWaveMacroCycleDemo.LegSegment segment, ElliottWaveAnalysisResult.WindowScenarioAssessment assessment,
            boolean bullish, boolean accepted) throws Exception {
        return ElliottWaveMacroCycleDemo.fitFromCoreAssessment(segment, assessment, bullish, accepted);
    }

    private static boolean isAnchoredToMacroEndpoints(BarSeries series,
            ElliottWaveMacroCycleDemo.SegmentScenarioFit segmentFit) {
        ElliottScenario scenario = segmentFit.scenario();
        int expectedStart = indexOf(series, segmentFit.segment().fromAnchor().at());
        int expectedEnd = indexOf(series, segmentFit.segment().toAnchor().at());
        int actualStart = scenario.swings().getFirst().fromIndex();
        int actualEnd = scenario.swings().getLast().toIndex();
        return Math.abs(actualStart - expectedStart) <= ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS
                && Math.abs(actualEnd - expectedEnd) <= ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS;
    }

    private static void assertAcceptedCoreRankedCycleFit(BarSeries series,
            ElliottWaveMacroCycleDemo.CycleFit cycleFit) {
        assertTrue(cycleFit.accepted());
        assertNotNull(cycleFit.bullishFit());
        assertNotNull(cycleFit.bearishFit());
        assertTrue(cycleFit.bullishFit().accepted());
        assertTrue(cycleFit.bearishFit().accepted());
        assertEquals("Core-ranked anchored-window impulse fit", cycleFit.bullishFit().rationale());
        assertEquals("Core-ranked anchored-window corrective fit", cycleFit.bearishFit().rationale());
        assertEquals(ElliottPhase.WAVE5, cycleFit.bullishFit().scenario().currentPhase());
        assertEquals(ElliottPhase.CORRECTIVE_C, cycleFit.bearishFit().scenario().currentPhase());
        assertEquals(5, cycleFit.bullishFit().scenario().swings().size());
        assertEquals(3, cycleFit.bearishFit().scenario().swings().size());
        assertBullishImpulseInvariantRules(cycleFit.bullishFit().scenario());
        assertBearishCorrectiveInvariantRules(cycleFit.bearishFit().scenario());
        assertTrue(isAnchoredToMacroEndpoints(series, cycleFit.bullishFit()));
        assertTrue(isAnchoredToMacroEndpoints(series, cycleFit.bearishFit()));
        assertTerminalPivotWithinTolerance(series, cycleFit.bullishFit());
        assertTerminalPivotWithinTolerance(series, cycleFit.bearishFit());
    }

    private static void assertTerminalPivotWithinTolerance(BarSeries series,
            ElliottWaveMacroCycleDemo.SegmentScenarioFit segmentFit) {
        int expectedTerminalIndex = indexOf(series, segmentFit.segment().toAnchor().at());
        int actualTerminalIndex = segmentFit.scenario().swings().getLast().toIndex();
        assertTrue(
                Math.abs(actualTerminalIndex
                        - expectedTerminalIndex) <= ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS,
                "Terminal pivot should stay within anchor tolerance for " + segmentFit.segment().toAnchor().id());
    }

    private static void assertScenarioInternalPivotsUseLocalExtremes(BarSeries series, ElliottScenario scenario,
            String label) {
        List<ElliottSwing> swings = scenario.swings();
        if (swings.size() < 2) {
            return;
        }

        int pivotCount = swings.size() + 1;
        int[] pivotIndices = new int[pivotCount];
        boolean[] highPivots = new boolean[pivotCount];
        pivotIndices[0] = swings.getFirst().fromIndex();
        highPivots[0] = false;
        for (int index = 0; index < swings.size(); index++) {
            ElliottSwing swing = swings.get(index);
            pivotIndices[index + 1] = swing.toIndex();
            highPivots[index + 1] = swing.isRising();
        }

        for (int pointIndex = 1; pointIndex < pivotCount - 1; pointIndex++) {
            int interiorStart = pivotIndices[pointIndex - 1] + 1;
            int interiorEnd = pivotIndices[pointIndex + 1] - 1;
            if (interiorStart > interiorEnd) {
                continue;
            }
            int expectedIndex = highPivots[pointIndex] ? highestHighIndex(series, interiorStart, interiorEnd)
                    : lowestLowIndex(series, interiorStart, interiorEnd);
            assertEquals(expectedIndex, pivotIndices[pointIndex],
                    "Expected dominant local pivot for " + label + " at point " + pointIndex);
        }
    }

    private static int indexOf(BarSeries series, Instant instant) {
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            if (series.getBar(index).getEndTime().equals(instant)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Missing bar for " + instant);
    }

    private static double datasetValueAtIndex(XYDataset dataset, int seriesIndex) {
        for (int itemIndex = 0; itemIndex < dataset.getItemCount(0); itemIndex++) {
            if ((int) Math.round(dataset.getXValue(0, itemIndex)) == seriesIndex) {
                return dataset.getYValue(0, itemIndex);
            }
        }
        throw new IllegalArgumentException("Missing plotted point at series index " + seriesIndex);
    }

    private static int highestHighIndex(BarSeries series, int startIndex, int endIndex) {
        int bestIndex = startIndex;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int index = startIndex; index <= endIndex; index++) {
            double candidate = series.getBar(index).getHighPrice().doubleValue();
            if (candidate > bestValue) {
                bestValue = candidate;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static int lowestLowIndex(BarSeries series, int startIndex, int endIndex) {
        int bestIndex = startIndex;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int index = startIndex; index <= endIndex; index++) {
            double candidate = series.getBar(index).getLowPrice().doubleValue();
            if (candidate < bestValue) {
                bestValue = candidate;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static void assertTruthTargetCycleFits(BarSeries series, List<ElliottWaveMacroCycleDemo.CycleFit> cycleFits,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        List<ExpectedTruthCycle> expectedCycles = expectedTruthCycles();
        List<ElliottWaveMacroCycleDemo.CycleFit> remainingCycleFits = new java.util.ArrayList<>(cycleFits);

        for (ExpectedTruthCycle expected : expectedCycles) {
            ElliottWaveMacroCycleDemo.CycleFit actual = findCycleFitByPeak(series, remainingCycleFits,
                    findAnchor(registry, expected.peakAnchorId()));

            assertAcceptedCoreRankedCycleFit(series, actual);
            assertEquals(expected.partition(), actual.cycle().partition());
            assertWithinTolerance(
                    series.getBar(actual.bullishFit().scenario().swings().getFirst().fromIndex()).getEndTime(),
                    findAnchor(registry, expected.startAnchorId()));
            assertWithinTolerance(
                    series.getBar(actual.bullishFit().scenario().swings().getLast().toIndex()).getEndTime(),
                    findAnchor(registry, expected.peakAnchorId()));
            assertWithinTolerance(
                    series.getBar(actual.bearishFit().scenario().swings().getLast().toIndex()).getEndTime(),
                    findAnchor(registry, expected.lowAnchorId()));
            remainingCycleFits.remove(actual);
        }
    }

    private static void assertTruthTargetCycleCoverage(BarSeries series,
            List<ElliottWaveMacroCycleDemo.CycleFit> cycleFits,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        List<ExpectedTruthCycle> expectedCycles = expectedTruthCycles();
        List<ElliottWaveMacroCycleDemo.CycleFit> remainingCycleFits = new java.util.ArrayList<>(cycleFits);

        for (ExpectedTruthCycle expected : expectedCycles) {
            ElliottWaveMacroCycleDemo.CycleFit actual = findCycleFitByPeak(series, remainingCycleFits,
                    findAnchor(registry, expected.peakAnchorId()));

            assertEquals(expected.partition(), actual.cycle().partition());
            assertEquals(expected.startAnchorId(), actual.cycle().bullishLeg().fromAnchor().id());
            assertWithinTolerance(
                    series.getBar(actual.bullishFit().scenario().swings().getLast().toIndex()).getEndTime(),
                    findAnchor(registry, expected.peakAnchorId()));
            assertWithinTolerance(
                    series.getBar(actual.bearishFit().scenario().swings().getLast().toIndex()).getEndTime(),
                    findAnchor(registry, expected.lowAnchorId()));
            remainingCycleFits.remove(actual);
        }
    }

    private static void assertTruthTargetCycleOrdering(List<ElliottWaveMacroCycleDemo.CycleFit> cycleFits,
            List<ExpectedTruthCycle> expectedCycles) {
        List<String> actualCycleIds = cycleFits.stream().map(cycleFit -> cycleFit.cycle().id()).toList();
        List<String> expectedCycleIds = expectedCycles.stream()
                .map(expected -> expected.startAnchorId() + "->" + expected.peakAnchorId() + "->"
                        + expected.lowAnchorId())
                .toList();
        assertEquals(expectedCycleIds, actualCycleIds);
    }

    private static ElliottWaveMacroCycleDemo.CycleFit findCycleFitByPeak(BarSeries series,
            List<ElliottWaveMacroCycleDemo.CycleFit> cycleFits, ElliottWaveAnchorCalibrationHarness.Anchor peakAnchor) {
        return cycleFits.stream()
                .filter(cycleFit -> isWithinTolerance(
                        series.getBar(cycleFit.bullishFit().scenario().swings().getLast().toIndex()).getEndTime(),
                        peakAnchor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing cycle fit for " + peakAnchor.id()));
    }

    private static void assertBullishImpulseInvariantRules(ElliottScenario scenario) {
        List<ElliottSwing> swings = scenario.swings();
        ElliottSwing wave1 = swings.get(0);
        ElliottSwing wave2 = swings.get(1);
        ElliottSwing wave4 = swings.get(3);

        assertTrue(wave2.toPrice().compareTo(wave1.fromPrice()) >= 0,
                "Wave 2 must not retrace below the start of wave 1");
        assertTrue(wave4.toPrice().compareTo(wave1.toPrice()) >= 0, "Wave 4 must not overlap wave 1 territory");

    }

    private static void assertBearishCorrectiveInvariantRules(ElliottScenario scenario) {
        List<ElliottSwing> swings = scenario.swings();
        assertFalse(swings.get(0).isRising(), "Corrective wave A should fall");
        assertTrue(swings.get(1).isRising(), "Corrective wave B should rise");
        assertFalse(swings.get(2).isRising(), "Corrective wave C should fall");
    }

    private static void assertWithinTolerance(Instant actual, ElliottWaveAnchorCalibrationHarness.Anchor anchor) {
        Instant windowStart = anchor.at().minus(anchor.toleranceBefore());
        Instant windowEnd = anchor.at().plus(anchor.toleranceAfter());

        assertFalse(actual.isBefore(windowStart),
                () -> actual + " is before tolerance window " + windowStart + " for " + anchor.id());
        assertFalse(actual.isAfter(windowEnd),
                () -> actual + " is after tolerance window " + windowEnd + " for " + anchor.id());
    }

    private static boolean isWithinTolerance(Instant actual, ElliottWaveAnchorCalibrationHarness.Anchor anchor) {
        Instant windowStart = anchor.at().minus(anchor.toleranceBefore());
        Instant windowEnd = anchor.at().plus(anchor.toleranceAfter());
        return !actual.isBefore(windowStart) && !actual.isAfter(windowEnd);
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor findAnchor(
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, String anchorId) {
        return registry.anchors()
                .stream()
                .filter(anchor -> anchor.id().equals(anchorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing anchor " + anchorId));
    }

    private static List<ExpectedTruthCycle> expectedTruthCycles() {
        return List.of(
                new ExpectedTruthCycle("validation", "btc-2011-cycle-bottom", "btc-2013-cycle-top",
                        "btc-2015-cycle-bottom"),
                new ExpectedTruthCycle("validation", "btc-2015-cycle-bottom", "btc-2017-cycle-top",
                        "btc-2018-cycle-bottom"),
                new ExpectedTruthCycle("holdout", "btc-2018-cycle-bottom", "btc-2021-cycle-top",
                        "btc-2022-cycle-bottom"));
    }

    private static List<ExpectedTruthAnchor> expectedTruthAnchors() {
        return List.of(
                new ExpectedTruthAnchor("btc-2011-cycle-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, ElliottPhase.WAVE5,
                        Instant.parse("2011-05-15T00:00:00Z"), Instant.parse("2011-07-15T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2011-cycle-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, ElliottPhase.CORRECTIVE_C,
                        Instant.parse("2011-10-15T00:00:00Z"), Instant.parse("2011-12-15T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2013-cycle-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, ElliottPhase.WAVE5,
                        Instant.parse("2013-11-20T00:00:00Z"), Instant.parse("2013-12-03T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2015-cycle-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, ElliottPhase.CORRECTIVE_C,
                        Instant.parse("2015-07-01T00:00:00Z"), Instant.parse("2015-09-30T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2017-cycle-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, ElliottPhase.WAVE5,
                        Instant.parse("2017-11-15T00:00:00Z"), Instant.parse("2018-01-15T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2018-cycle-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, ElliottPhase.CORRECTIVE_C,
                        Instant.parse("2018-11-01T00:00:00Z"), Instant.parse("2019-02-15T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2021-cycle-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT, ElliottPhase.WAVE5,
                        Instant.parse("2021-10-01T00:00:00Z"), Instant.parse("2021-12-15T00:00:00Z")),
                new ExpectedTruthAnchor("btc-2022-cycle-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                        ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT, ElliottPhase.CORRECTIVE_C,
                        Instant.parse("2022-10-15T00:00:00Z"), Instant.parse("2022-12-31T00:00:00Z")));
    }

    private record ExpectedTruthCycle(String partition, String startAnchorId, String peakAnchorId, String lowAnchorId) {
    }

    private record ExpectedTruthAnchor(String id, ElliottWaveAnchorCalibrationHarness.AnchorType type,
            ElliottWaveAnchorRegistry.AnchorPartition partition, ElliottPhase expectedPhase, Instant windowStart,
            Instant windowEnd) {
    }

    private static BarSeries loadBitcoinSeries() {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                LOG);
        assertNotNull(series);
        return series;
    }

    private static BarSeries trailingWindow(BarSeries fullSeries, int lookbackBars) {
        int windowStart = Math.max(fullSeries.getBeginIndex(), fullSeries.getEndIndex() - lookbackBars + 1);
        return fullSeries.getSubSeries(windowStart, fullSeries.getEndIndex() + 1);
    }

    private Path newTempDirectory(String name) throws IOException {
        return Files.createDirectories(tempDirectory.resolve(name));
    }

    private static BarSeries chartSyntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-chart-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 100.0, 102.0, 96.0, 101.0 }, { 101.0, 126.0, 100.0, 124.0 },
                { 124.0, 125.0, 112.0, 114.0 }, { 114.0, 150.0, 113.0, 148.0 }, { 148.0, 170.0, 147.0, 168.0 },
                { 168.0, 169.0, 132.0, 136.0 }, { 136.0, 138.0, 108.0, 110.0 }, { 110.0, 128.0, 109.0, 126.0 },
                { 126.0, 144.0, 124.0, 142.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEndTime.plus(Duration.ofDays(index)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }

    private static BarSeries studySyntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-study-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 140.0, 142.0, 132.0, 136.0 }, { 136.0, 170.0, 134.0, 166.0 },
                { 166.0, 168.0, 118.0, 122.0 }, { 122.0, 136.0, 120.0, 132.0 }, { 132.0, 168.0, 130.0, 164.0 },
                { 164.0, 205.0, 162.0, 202.0 }, { 202.0, 204.0, 156.0, 160.0 }, { 160.0, 162.0, 112.0, 116.0 },
                { 116.0, 144.0, 114.0, 142.0 }, { 142.0, 174.0, 140.0, 171.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEndTime.plus(Duration.ofDays(index)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }

    private static BarSeries extendedSyntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-extended-chart-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 130.0, 136.0, 128.0, 134.0 }, { 134.0, 135.0, 112.0, 114.0 },
                { 114.0, 125.0, 113.0, 122.0 }, { 122.0, 152.0, 121.0, 150.0 }, { 150.0, 151.0, 138.0, 140.0 },
                { 140.0, 141.0, 100.0, 102.0 }, { 102.0, 122.0, 101.0, 118.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEndTime.plus(Duration.ofDays(index)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }

    private static BufferedImage readImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unable to decode image " + path);
        }
        return image;
    }

    private static XYItemRenderer findRenderer(XYPlot plot, String seriesKey) {
        XYDataset dataset = findDataset(plot, seriesKey);
        if (dataset == null) {
            return null;
        }
        for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
            if (plot.getDataset(datasetIndex) == dataset) {
                return plot.getRenderer(datasetIndex);
            }
        }
        return null;
    }

    private static XYDataset findDataset(XYPlot plot, String seriesKey) {
        for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
            XYDataset dataset = plot.getDataset(datasetIndex);
            if (dataset == null || dataset.getSeriesCount() == 0) {
                continue;
            }
            if (seriesKey.equals(dataset.getSeriesKey(0).toString())) {
                return dataset;
            }
        }
        return null;
    }

    private static void assertPaintMatches(Color expected, java.awt.Paint paint) {
        Color actual = assertInstanceOf(Color.class, paint);
        assertEquals(expected.getRed(), actual.getRed());
        assertEquals(expected.getGreen(), actual.getGreen());
        assertEquals(expected.getBlue(), actual.getBlue());
        assertTrue(actual.getAlpha() > 0);
    }
}

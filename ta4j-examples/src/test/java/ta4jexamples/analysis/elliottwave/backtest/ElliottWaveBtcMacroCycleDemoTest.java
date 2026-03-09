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

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

        List<BarLabel> bullishLabels = ElliottWaveBtcMacroCycleDemo.buildWaveLabelsFromScenario(series, bullishScenario,
                ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR);
        List<BarLabel> correctiveLabels = ElliottWaveBtcMacroCycleDemo.buildWaveLabelsFromScenario(series,
                correctiveScenario, ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR);

        assertEquals(List.of("1", "2", "3", "4", "5"), bullishLabels.stream().map(BarLabel::text).toList());
        assertEquals(List.of("A", "B", "C"), correctiveLabels.stream().map(BarLabel::text).toList());
    }

    @Test
    void fitFromCoreAssessmentUsesCoreCompositeAsPrimaryAcceptanceGate() throws Exception {
        BarSeries series = studySyntheticSeries();
        ElliottWaveBtcMacroCycleDemo.LegSegment segment = new ElliottWaveBtcMacroCycleDemo.LegSegment(
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

        ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit fit = invokeFitFromCoreAssessment(segment, assessment, true, 2,
                7);

        assertTrue(fit.accepted());
        assertTrue(fit.ruleScore() < 0.35);
        assertEquals("Core-ranked anchored-window impulse fit", fit.rationale());
    }

    @Test
    void fitFromCoreAssessmentDoesNotRequireDemoStrengthThresholdWhenCompositeIsHigh() throws Exception {
        BarSeries series = studySyntheticSeries();
        ElliottWaveBtcMacroCycleDemo.LegSegment segment = new ElliottWaveBtcMacroCycleDemo.LegSegment(
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

        ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit fit = invokeFitFromCoreAssessment(segment, assessment, true, 2,
                7);

        assertTrue(fit.accepted());
        assertTrue(fit.strengthScore() < 0.55);
        assertEquals("Core-ranked anchored-window impulse fit", fit.rationale());
    }

    @Test
    void evaluateMacroStudyProducesProfileTableCycleSummaryAndCurrentCycleFallback() {
        BarSeries series = studySyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 1),
                        anchor("btc-bottom-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 2),
                        anchor("btc-top-2013", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 5),
                        anchor("btc-bottom-2015", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 7)));

        ElliottWaveBtcMacroCycleDemo.MacroStudy study = ElliottWaveBtcMacroCycleDemo.evaluateMacroStudy(series,
                registry);

        assertEquals(5, study.profileScores().size());
        assertEquals(1, study.cycles().size());
        assertEquals("Bullish 1-2-3-4-5", study.cycles().getFirst().impulseLabel());
        assertEquals("Bearish A-B-C", study.cycles().getFirst().correctionLabel());
        assertEquals(4, study.hypotheses().size());
        assertFalse(study.selectedProfile().profile().id().isBlank());
        assertEquals(series.getBar(7).getEndTime().toString(), study.currentCycle().startTimeUtc());
        assertEquals(study.selectedProfile().profile().id(), study.currentCycle().winningProfileId());
        if (study.currentPrimaryFit() != null) {
            assertFalse(study.currentPrimaryFit().scenario().swings().isEmpty());
        }
    }

    @Test
    void saveMacroCycleChartWritesImage() throws Exception {
        BarSeries series = chartSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 0),
                        anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 4),
                        anchor("btc-low", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 6)));
        Path tempDir = Files.createTempDirectory("btc-macro-cycle-demo");

        try {
            Optional<Path> savedPath = ElliottWaveBtcMacroCycleDemo.saveMacroCycleChart(series, registry, tempDir);

            assertTrue(savedPath.isPresent());
            assertTrue(Files.exists(savedPath.get()));
            assertTrue(savedPath.get().getFileName().toString().endsWith(".jpg"));
            BufferedImage image = readImage(savedPath.get());
            assertEquals(ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, image.getWidth());
            assertEquals(ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT, image.getHeight());
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void realDatasetHistoricalCyclesProduceAcceptedFitsAndPersistArtifacts() throws Exception {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class));
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        Path tempDir = Files.createTempDirectory("btc-macro-cycle-real");

        try {
            ElliottWaveBtcMacroCycleDemo.MacroStudy study = ElliottWaveBtcMacroCycleDemo.evaluateMacroStudy(series,
                    registry);
            ElliottWaveBtcMacroCycleDemo.DemoReport report = ElliottWaveBtcMacroCycleDemo.generateReport(tempDir);

            assertTrue(study.selectedProfile().historicalFitPassed());
            assertEquals(3, study.cycles().size());
            assertTrue(
                    study.cycles().stream().allMatch(ElliottWaveBtcMacroCycleDemo.DirectionalCycleSummary::accepted));
            assertTrue(study.cycles().stream().allMatch(cycle -> "accepted historical fit".equals(cycle.status())));
            assertEquals(3, study.selectedProfile().cycleFits().size());
            study.selectedProfile().cycleFits().forEach(cycleFit -> assertAcceptedCoreRankedCycleFit(series, cycleFit));
            assertTrue(study.selectedProfile().chartSegments().size() >= 6);
            assertTrue(study.selectedProfile()
                    .chartSegments()
                    .stream()
                    .allMatch(ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit::accepted));
            assertTrue(study.selectedProfile()
                    .chartSegments()
                    .stream()
                    .allMatch(segment -> isAnchoredToMacroEndpoints(series, segment)));
            assertTrue(study.selectedProfile()
                    .chartSegments()
                    .stream()
                    .allMatch(segment -> segment.rationale().startsWith("Core-ranked anchored-window")));
            Optional<ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit> coreBullSegment = study.selectedProfile()
                    .chartSegments()
                    .stream()
                    .filter(segment -> segment.segment().bullish())
                    .filter(segment -> segment.segment().fromAnchor().id().equals("btc-2015-cycle-bottom"))
                    .filter(segment -> segment.segment().toAnchor().id().equals("btc-2017-cycle-top"))
                    .findFirst();
            assertTrue(coreBullSegment.isPresent());
            assertEquals("Core-ranked anchored-window impulse fit", coreBullSegment.orElseThrow().rationale());
            assertFalse(study.currentCycle().currentWave().isBlank());
            assertTrue(Files.exists(Path.of(report.chartPath())));
            assertTrue(Files.exists(Path.of(report.summaryPath())));
            assertEquals(report.chartPath(), report.currentCycle().chartPath());
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void genericMacroCycleDemoMatchesBtcWrapperOnFullBitcoinHistory() throws Exception {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class));
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        Path wrapperDir = Files.createTempDirectory("btc-macro-wrapper");
        Path genericDir = Files.createTempDirectory("btc-macro-generic");

        try {
            ElliottWaveBtcMacroCycleDemo.DemoReport wrapperReport = ElliottWaveBtcMacroCycleDemo
                    .generateReport(wrapperDir);
            ElliottWaveBtcMacroCycleDemo.DemoReport genericReport = ElliottWaveMacroCycleDemo
                    .generateHistoricalReport(series, registry, genericDir);

            assertEquals(wrapperReport.selectedProfileId(), genericReport.selectedProfileId());
            assertEquals(wrapperReport.selectedHypothesisId(), genericReport.selectedHypothesisId());
            assertEquals(wrapperReport.historicalFitPassed(), genericReport.historicalFitPassed());
            assertEquals(wrapperReport.profileScores(), genericReport.profileScores());
            assertEquals(wrapperReport.cycles(), genericReport.cycles());
            assertEquals(wrapperReport.hypotheses(), genericReport.hypotheses());
            assertEquals(wrapperReport.currentCycle().startTimeUtc(), genericReport.currentCycle().startTimeUtc());
            assertEquals(wrapperReport.currentCycle().winningProfileId(),
                    genericReport.currentCycle().winningProfileId());
            assertEquals(wrapperReport.currentCycle().primaryCount(), genericReport.currentCycle().primaryCount());
            assertEquals(wrapperReport.currentCycle().alternateCount(), genericReport.currentCycle().alternateCount());
            assertEquals(wrapperReport.currentCycle().currentWave(), genericReport.currentCycle().currentWave());
            assertEquals(wrapperReport.currentCycle().invalidationPrice(),
                    genericReport.currentCycle().invalidationPrice());
            assertEquals(wrapperReport.currentCycle().structuralInvalidationPrice(),
                    genericReport.currentCycle().structuralInvalidationPrice());
            assertTrue(Files.exists(Path.of(genericReport.chartPath())));
            assertTrue(Files.exists(Path.of(genericReport.summaryPath())));
        } finally {
            Files.walk(wrapperDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
            Files.walk(genericDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void fullBitcoinHistoryKeepsStableMacroOutlook() throws Exception {
        Path tempDir = Files.createTempDirectory("btc-macro-outlook-stability");

        try {
            ElliottWaveBtcMacroCycleDemo.DemoReport report = ElliottWaveBtcMacroCycleDemo.generateReport(tempDir);

            assertEquals("orthodox-classical", report.selectedProfileId());
            assertEquals("H0", report.selectedHypothesisId());
            assertTrue(report.historicalFitPassed());
            assertEquals("Bullish 1-2-3-4", report.currentCycle().primaryCount());
            assertEquals("WAVE4", report.currentCycle().currentWave());
            assertEquals("<= 68997.75", report.currentCycle().invalidationPrice());
            assertEquals("<= 3709.55", report.currentCycle().structuralInvalidationPrice());
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void livePresetReportFindsCurrentCycleStartFromProvidedSeriesWindow() throws Exception {
        BarSeries fullSeries = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class));
        int lookbackBars = 1825;
        int windowStart = Math.max(fullSeries.getBeginIndex(), fullSeries.getEndIndex() - lookbackBars + 1);
        BarSeries liveWindow = fullSeries.getSubSeries(windowStart, fullSeries.getEndIndex() + 1);
        Path tempDir = Files.createTempDirectory("btc-live-macro-preset");

        try {
            ElliottWaveBtcMacroCycleDemo.LivePresetReport report = ElliottWaveBtcMacroCycleDemo
                    .generateLivePresetReport(liveWindow, tempDir);

            Instant discoveredStart = Instant.parse(report.currentCycle().startTimeUtc());
            Instant expectedWindowStart = liveWindow.getFirstBar().getEndTime();
            Instant expectedWindowEnd = liveWindow.getLastBar().getEndTime();

            assertTrue(!discoveredStart.isBefore(expectedWindowStart) && !discoveredStart.isAfter(expectedWindowEnd));
            assertFalse(report.currentCycle().startTimeUtc().isBlank());
            assertTrue(report.currentCycle().primaryCount().startsWith("Bullish 1"));
            assertFalse(report.currentCycle().currentWave().isBlank());
            assertTrue(Files.exists(Path.of(report.chartPath())));
            assertTrue(Files.exists(Path.of(report.summaryPath())));
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void runLivePresetRestoresLegacyBaseCaseAndAlternativeCharts() throws Exception {
        BarSeries fullSeries = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class));
        int lookbackBars = 1825;
        int windowStart = Math.max(fullSeries.getBeginIndex(), fullSeries.getEndIndex() - lookbackBars + 1);
        BarSeries liveWindow = fullSeries.getSubSeries(windowStart, fullSeries.getEndIndex() + 1);
        Path tempDir = Files.createTempDirectory("btc-live-preset-legacy");

        try {
            ElliottWaveBtcMacroCycleDemo.runLivePreset(liveWindow, tempDir);

            assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-base-case.jpg")));
            assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-1.jpg")));
            assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-2.jpg")));
            assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-3.jpg")));
            assertTrue(Files.exists(tempDir.resolve("elliott-wave-analysis-btc-usd-cycle-alternative-4.jpg")));
            assertTrue(Files.exists(tempDir.resolve(ElliottWaveBtcMacroCycleDemo.DEFAULT_LIVE_SUMMARY_FILE_NAME)));
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void macroLogicProfilesStayNonPublicWhileSelectionSurfaceIsUnsettled() throws Exception {
        Method profilesMethod = ElliottWaveBtcMacroCycleDemo.class.getDeclaredMethod("logicProfiles");
        Method defaultProfileMethod = ElliottWaveBtcMacroCycleDemo.class.getDeclaredMethod("defaultLiveMacroProfile");

        assertFalse(Modifier.isPublic(ElliottWaveBtcMacroCycleDemo.MacroLogicProfile.class.getModifiers()));
        assertTrue(Modifier.isPrivate(profilesMethod.getModifiers()));
        assertTrue(Modifier.isPrivate(defaultProfileMethod.getModifiers()));
    }

    @Test
    void liveCurrentCycleCandidatesKeepAlternatingBullishSwingProgression() throws Exception {
        BarSeries fullSeries = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class));
        int lookbackBars = 1825;
        int windowStart = Math.max(fullSeries.getBeginIndex(), fullSeries.getEndIndex() - lookbackBars + 1);
        BarSeries liveWindow = fullSeries.getSubSeries(windowStart, fullSeries.getEndIndex() + 1);

        Method profileMethod = ElliottWaveBtcMacroCycleDemo.class.getDeclaredMethod("defaultLiveMacroProfile");
        profileMethod.setAccessible(true);
        Object profile = profileMethod.invoke(null);

        Method evaluateMethod = ElliottWaveBtcMacroCycleDemo.class.getDeclaredMethod("evaluateCurrentCycle",
                BarSeries.class, profile.getClass(), String.class);
        evaluateMethod.setAccessible(true);
        ElliottWaveBtcMacroCycleDemo.CurrentCycleAnalysis analysis = (ElliottWaveBtcMacroCycleDemo.CurrentCycleAnalysis) evaluateMethod
                .invoke(null, liveWindow, profile, "test");

        assertFalse(analysis.candidates().isEmpty());
        analysis.candidates().stream().limit(5).forEach(candidate -> {
            List<ElliottSwing> swings = candidate.fit().scenario().swings();
            assertFalse(swings.isEmpty());
            assertTrue(Math.abs(swings.getFirst().fromIndex() - candidate.startIndex()) <= 3,
                    "Candidate should stay anchored near its chosen cycle start");
            assertTrue(Math.abs(swings.getLast().toIndex() - liveWindow.getEndIndex()) <= 3,
                    "Candidate should stay anchored near the live window end");
            for (int index = 0; index < swings.size(); index++) {
                ElliottSwing swing = swings.get(index);
                assertEquals(index % 2 == 0, swing.isRising(),
                        "Expected alternating bullish progression for " + candidate.fit().countLabel());
                if (index == 0) {
                    continue;
                }
                ElliottSwing previous = swings.get(index - 1);
                assertEquals(previous.toIndex(), swing.fromIndex(),
                        "Swing indices should stay contiguous for " + candidate.fit().countLabel());
                assertEquals(previous.toPrice(), swing.fromPrice(),
                        "Swing prices should stay anchored for " + candidate.fit().countLabel());
            }
            assertScenarioInternalPivotsUseLocalExtremes(liveWindow, candidate.fit().scenario(),
                    candidate.fit().countLabel());
        });
    }

    @Test
    void renderMacroCycleChartUsesLogAxisOnMainPricePlot() {
        BarSeries series = chartSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 0),
                        anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 4),
                        anchor("btc-low", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 6)));

        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);

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
    void renderMacroCycleChartDrawsLeadingBearishPreludeAndIntermediateBullCycle() {
        BarSeries series = extendedSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-1", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 0),
                        anchor("btc-bottom-1", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 1),
                        anchor("btc-top-2", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 3),
                        anchor("btc-bottom-2", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 5)));

        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);
        XYPlot mainPlot = (XYPlot) ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().getFirst();

        XYDataset bullishDataset = findDataset(mainPlot, "Bullish 1-2-3-4-5");
        XYDataset bearishDataset = findDataset(mainPlot, "Bearish A-B-C");

        assertNotNull(bullishDataset);
        assertNotNull(bearishDataset);
        assertEquals(1, bullishDataset.getSeriesCount());
        assertEquals(2, bearishDataset.getSeriesCount());
    }

    @Test
    void interpolateOverlayPriceUsesLogSpaceOnPositivePrices() {
        double midpoint = ElliottWaveBtcMacroCycleDemo.interpolateOverlayPrice(100.0, 1600.0, 0.5);

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

    private static ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit invokeFitFromCoreAssessment(
            ElliottWaveBtcMacroCycleDemo.LegSegment segment,
            ElliottWaveAnalysisResult.WindowScenarioAssessment assessment, boolean bullish, int startIndex,
            int endIndex) throws Exception {
        Method profilesMethod = ElliottWaveBtcMacroCycleDemo.class.getDeclaredMethod("logicProfiles");
        profilesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ElliottWaveBtcMacroCycleDemo.MacroLogicProfile> profiles = (List<ElliottWaveBtcMacroCycleDemo.MacroLogicProfile>) profilesMethod
                .invoke(null);
        Method method = ElliottWaveBtcMacroCycleDemo.class.getDeclaredMethod("fitFromCoreAssessment",
                ElliottWaveBtcMacroCycleDemo.LegSegment.class, ElliottWaveBtcMacroCycleDemo.MacroLogicProfile.class,
                ElliottWaveAnalysisResult.WindowScenarioAssessment.class, boolean.class, int.class, int.class);
        method.setAccessible(true);
        return (ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit) method.invoke(null, segment, profiles.getFirst(),
                assessment, bullish, startIndex, endIndex);
    }

    private static boolean isAnchoredToMacroEndpoints(BarSeries series,
            ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit segmentFit) {
        ElliottScenario scenario = segmentFit.scenario();
        int expectedStart = indexOf(series, segmentFit.segment().fromAnchor().at());
        int expectedEnd = indexOf(series, segmentFit.segment().toAnchor().at());
        int actualStart = scenario.swings().getFirst().fromIndex();
        int actualEnd = scenario.swings().getLast().toIndex();
        return Math.abs(actualStart - expectedStart) <= ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS
                && Math.abs(actualEnd - expectedEnd) <= ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS;
    }

    private static void assertAcceptedCoreRankedCycleFit(BarSeries series,
            ElliottWaveBtcMacroCycleDemo.CycleFit cycleFit) {
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
        assertTrue(isAnchoredToMacroEndpoints(series, cycleFit.bullishFit()));
        assertTrue(isAnchoredToMacroEndpoints(series, cycleFit.bearishFit()));
        assertTerminalPivotWithinTolerance(series, cycleFit.bullishFit());
        assertTerminalPivotWithinTolerance(series, cycleFit.bearishFit());
    }

    private static void assertTerminalPivotWithinTolerance(BarSeries series,
            ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit segmentFit) {
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

/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.ta4j.core.num.NaN.NaN;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottLogicProfile;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.CurrentCycleAnalysis;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.CurrentCycleSummary;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.CycleFit;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.DemoReport;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.DirectionalCycleSummary;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.HypothesisResult;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.LegSegment;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.MacroCycle;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.MacroLogicProfile;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.MacroProfileEvaluation;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.MacroStudy;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.ProfileScoreSummary;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveBtcMacroCycleDemo.SegmentScenarioFit;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Generic macro-cycle demo facade for any instrument and timespan.
 *
 * <p>
 * Callers can either provide the exact {@link BarSeries} window plus an
 * {@link ElliottWaveAnchorCalibrationHarness.AnchorRegistry} that describes the
 * cycle anchors they want to validate, or let the demo infer broad macro turns
 * directly from the series. Both paths reuse the same historical study, chart
 * rendering, and JSON reporting flow that the BTC wrapper now delegates to.
 *
 * @since 0.22.4
 */
public final class ElliottWaveMacroCycleDemo {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveMacroCycleDemo.class);

    private ElliottWaveMacroCycleDemo() {
    }

    /**
     * Runs the historical macro-cycle report using anchors inferred directly from
     * the supplied series.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated macro-cycle report
     * @since 0.22.4
     */
    public static DemoReport generateHistoricalReport(final BarSeries series, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        return generateHistoricalReport(series, ElliottWaveMacroCycleDetector.inferAnchorRegistry(series),
                chartDirectory);
    }

    /**
     * Runs the historical macro-cycle report for the supplied series and anchor
     * registry.
     *
     * @param series         series window to analyze
     * @param registry       anchor registry describing the macro-cycle turns to
     *                       validate
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated macro-cycle report
     * @since 0.22.4
     */
    public static DemoReport generateHistoricalReport(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final CanonicalStructure structure = analyzeCanonicalStructure(series, registry);
        final MacroStudy study = structure.historicalStudy().orElseThrow();
        final Optional<Path> chartPath = saveHistoricalChart(series, registry, study, chartDirectory);
        final String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        final Path summaryPath = chartDirectory.resolve(ElliottWaveBtcMacroCycleDemo.DEFAULT_SUMMARY_FILE_NAME)
                .toAbsolutePath()
                .normalize();
        final String baselineProfileId = ElliottWaveAnchorCalibrationHarness.canonicalBtcCalibratedProfile().id();
        final CurrentCycleSummary currentCycle = structure.currentCycle().summary().withChartPath(chartPathText);
        final DemoReport report = new DemoReport(registry.version(), registry.datasetResource(), baselineProfileId,
                study.selectedProfile().profile().id(), study.selectedProfile().profile().hypothesisId(),
                study.selectedProfile().historicalFitPassed(),
                "Macro-cycle decomposition selected from core-ranked anchor-to-anchor wave fits", chartPathText,
                summaryPath.toString(), study.profileScores(), study.cycles(), study.hypotheses(), currentCycle);
        saveSummary(report.toJson(), summaryPath, "macro-cycle summary");
        return report;
    }

    /**
     * Runs a live current-cycle macro report directly on the supplied series using
     * generic instrument-aware output names.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered charts and JSON summary
     * @since 0.22.4
     */
    public static void runLivePreset(final BarSeries series, final Path chartDirectory) {
        final String seriesToken = scenarioSeriesName(series);
        runLivePreset(series, chartDirectory, liveCurrentCycleChartFileName(seriesToken),
                liveSummaryFileName(seriesToken), seriesToken,
                "Series-native current-cycle inference using the default orthodox macro profile");
    }

    /**
     * Generates the live current-cycle report directly on the supplied series using
     * generic instrument-aware output names.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated live current-cycle report
     * @since 0.22.4
     */
    public static ElliottWaveBtcMacroCycleDemo.LivePresetReport generateLivePresetReport(final BarSeries series,
            final Path chartDirectory) {
        final String seriesToken = scenarioSeriesName(series);
        return generateLivePresetReport(series, chartDirectory, liveCurrentCycleChartFileName(seriesToken),
                liveSummaryFileName(seriesToken), seriesToken,
                "Series-native current-cycle inference using the default orthodox macro profile");
    }

    static void runLivePreset(final BarSeries series, final Path chartDirectory, final String chartFileName,
            final String summaryFileName, final String scenarioSeriesToken, final String historicalStatus) {
        final LivePresetExecution execution = analyzeLivePreset(series, chartDirectory, chartFileName, summaryFileName,
                historicalStatus);
        final LivePresetLegacyView legacyView = generateLivePresetLegacyView(series, scenarioSeriesToken,
                execution.currentCycle(), execution.report(), chartDirectory);
        logLegacyCompatibleLivePreset(legacyView);
    }

    static ElliottWaveBtcMacroCycleDemo.LivePresetReport generateLivePresetReport(final BarSeries series,
            final Path chartDirectory, final String chartFileName, final String summaryFileName,
            final String scenarioSeriesToken, final String historicalStatus) {
        return analyzeLivePreset(series, chartDirectory, chartFileName, summaryFileName, historicalStatus).report();
    }

    /**
     * Saves the rendered macro-cycle chart using anchors inferred directly from the
     * supplied series.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the saved chart image
     * @return saved chart path when rendering succeeds
     * @since 0.22.4
     */
    public static Optional<Path> saveHistoricalChart(final BarSeries series, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        return saveHistoricalChart(series, ElliottWaveMacroCycleDetector.inferAnchorRegistry(series), chartDirectory);
    }

    /**
     * Saves the rendered macro-cycle chart for the supplied series and anchors.
     *
     * @param series         series window to analyze
     * @param registry       anchor registry describing the macro-cycle turns to
     *                       validate
     * @param chartDirectory directory for the saved chart image
     * @return saved chart path when rendering succeeds
     * @since 0.22.4
     */
    public static Optional<Path> saveHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final MacroStudy study = evaluateMacroStudy(series, registry);
        return saveHistoricalChart(series, registry, study, chartDirectory);
    }

    /**
     * Renders the macro-cycle chart using anchors inferred directly from the
     * supplied series.
     *
     * @param series series window to analyze
     * @return rendered chart
     * @since 0.22.4
     */
    public static JFreeChart renderHistoricalChart(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        return renderHistoricalChart(series, ElliottWaveMacroCycleDetector.inferAnchorRegistry(series));
    }

    /**
     * Renders the macro-cycle chart for the supplied series and anchors without
     * writing files.
     *
     * @param series   series window to analyze
     * @param registry anchor registry describing the macro-cycle turns to validate
     * @return rendered chart
     * @since 0.22.4
     */
    public static JFreeChart renderHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        final MacroStudy study = evaluateMacroStudy(series, registry);
        return renderHistoricalChart(series, registry, study);
    }

    static MacroStudy evaluateMacroStudy(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");

        final List<MacroLogicProfile> profiles = logicProfiles();
        final List<LegSegment> chartLegs = buildLegSegments(registry);
        final List<MacroCycle> historicalCycles = buildHistoricalCycles(registry);

        final List<MacroProfileEvaluation> evaluations = new ArrayList<>();
        for (final MacroLogicProfile profile : profiles) {
            evaluations.add(evaluateProfile(series, profile, chartLegs, historicalCycles));
        }
        evaluations.sort(profileEvaluationComparator());
        final MacroProfileEvaluation selectedProfile = evaluations.getFirst();
        final List<ProfileScoreSummary> profileScores = evaluations.stream().map(ProfileScoreSummary::from).toList();
        final List<DirectionalCycleSummary> cycles = selectedProfile.cycleFits()
                .stream()
                .map(DirectionalCycleSummary::from)
                .toList();
        final List<HypothesisResult> hypotheses = buildHypotheses(evaluations);
        final CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, selectedProfile.profile(),
                selectedProfile.historicalFitPassed() ? "historical BTC fit passed"
                        : "historical BTC fit still partial");
        return new MacroStudy(selectedProfile, List.copyOf(evaluations), profileScores, cycles, hypotheses,
                currentCycle);
    }

    static CanonicalStructure analyzeCanonicalStructure(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        final MacroStudy study = evaluateMacroStudy(series, registry);
        return new CanonicalStructure(Optional.of(study), study.currentCycleAnalysis());
    }

    static CurrentCycleAnalysis evaluateCurrentCycle(final BarSeries series, final MacroLogicProfile profile,
            final String historicalStatus) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(historicalStatus, "historicalStatus");

        final ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        final ElliottWaveAnalysisResult.CurrentCycleAssessment assessment = profileRunner.analyzeCurrentCycle(series);
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment primary = assessment.primary();
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment alternate = assessment.alternate();
        final int startIndex = assessment.startIndex();
        final String primaryCount = primary == null ? "No current bullish count" : primary.countLabel();
        final String alternateCount = alternate == null ? "" : alternate.countLabel();
        final String currentWave = primary == null ? "" : primary.currentPhase().name();
        final String invalidation = primary == null ? ""
                : formatInvalidationCondition(primary.scenario(), primary.phaseInvalidationPrice());
        final String structuralInvalidation = primary == null ? ""
                : formatInvalidationCondition(primary.scenario(), primary.invalidationPrice());
        final String startTimeUtc = series.getBar(startIndex).getEndTime().toString();
        final String latestTimeUtc = series.getLastBar().getEndTime().toString();
        final CurrentCycleSummary summary = new CurrentCycleSummary(startTimeUtc, latestTimeUtc, profile.id(),
                historicalStatus, primaryCount, alternateCount, currentWave, invalidation, structuralInvalidation,
                orthodoxWaveFiveTargetRange(primary), primary == null ? 0.0 : primary.fitScore(),
                alternate == null ? 0.0 : alternate.fitScore(), "");
        return new CurrentCycleAnalysis(summary, primary, alternate, assessment.candidates(),
                assessment.distinctCandidates(5));
    }

    private static LivePresetExecution analyzeLivePreset(final BarSeries series, final Path chartDirectory,
            final String chartFileName, final String summaryFileName, final String historicalStatus) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        Objects.requireNonNull(chartFileName, "chartFileName");
        Objects.requireNonNull(summaryFileName, "summaryFileName");
        Objects.requireNonNull(historicalStatus, "historicalStatus");

        final MacroLogicProfile profile = defaultLiveMacroProfile();
        final CanonicalStructure structure = analyzeCanonicalStructure(series, profile, historicalStatus);
        final CurrentCycleAnalysis currentCycle = structure.currentCycle();
        final Optional<Path> chartPath = saveLiveCurrentCycleChart(series, currentCycle, chartDirectory, chartFileName);
        final String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        final Path summaryPath = chartDirectory.resolve(summaryFileName).toAbsolutePath().normalize();
        final CurrentCycleSummary summary = currentCycle.summary().withChartPath(chartPathText);
        final ElliottWaveBtcMacroCycleDemo.LivePresetReport report = new ElliottWaveBtcMacroCycleDemo.LivePresetReport(
                series.getName(), series.getFirstBar().getEndTime().toString(),
                series.getLastBar().getEndTime().toString(), profile.id(), profile.hypothesisId(), chartPathText,
                summaryPath.toString(), summary);
        saveSummary(report.toJson(), summaryPath, "live macro summary");
        return new LivePresetExecution(currentCycle.withSummary(summary), report);
    }

    static CanonicalStructure analyzeCanonicalStructure(final BarSeries series, final MacroLogicProfile profile,
            final String historicalStatus) {
        return new CanonicalStructure(Optional.empty(), evaluateCurrentCycle(series, profile, historicalStatus));
    }

    static List<MacroLogicProfile> logicProfiles() {
        return List.of(
                new MacroLogicProfile("orthodox-classical", "H0", "Classical Elliott constraints", 0,
                        ElliottLogicProfile.ORTHODOX_CLASSICAL, ElliottDegree.MINOR),
                new MacroLogicProfile("h1-hierarchical-swing", "H1", "Hierarchical swing extraction", 1,
                        ElliottLogicProfile.HIERARCHICAL_SWING, ElliottDegree.MINOR),
                new MacroLogicProfile("h2-btc-relaxed-impulse", "H2", "Relaxed impulse rules for BTC", 2,
                        ElliottLogicProfile.BTC_RELAXED_IMPULSE, ElliottDegree.MINOR),
                new MacroLogicProfile("h3-btc-relaxed-corrective", "H3", "Relaxed corrective coverage for BTC", 3,
                        ElliottLogicProfile.BTC_RELAXED_CORRECTIVE, ElliottDegree.MINOR),
                new MacroLogicProfile("h4-anchor-first-hybrid", "H4", "Anchor-first hybrid profile", 4,
                        ElliottLogicProfile.ANCHOR_FIRST_HYBRID, ElliottDegree.MINOR));
    }

    static MacroLogicProfile defaultLiveMacroProfile() {
        return logicProfiles().stream()
                .filter(profile -> profile.id().equals("orthodox-classical"))
                .findFirst()
                .orElseGet(() -> logicProfiles().getFirst());
    }

    static SegmentScenarioFit fitFromCoreAssessment(final LegSegment legSegment, final MacroLogicProfile profile,
            final ElliottWaveAnalysisResult.WindowScenarioAssessment assessment, final boolean bullish,
            final boolean accepted) {
        final ElliottScenario scenario = assessment.scenario();
        final double structureScore = assessment.structureScore();
        final double ruleScore = assessment.ruleScore();
        final double spacingScore = assessment.spacingScore();
        final double strengthScore = assessment.strengthScore();
        final double fitScore = assessment.fitScore();
        return new SegmentScenarioFit(legSegment, scenario, fitScore, structureScore, ruleScore, spacingScore,
                strengthScore, bullish, accepted,
                bullish ? "Core-ranked anchored-window impulse fit" : "Core-ranked anchored-window corrective fit");
    }

    static List<BarLabel> buildWaveLabelsFromScenario(final BarSeries series, final ElliottScenario scenario,
            final Color labelColor) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(labelColor, "labelColor");

        final List<ElliottSwing> swings = scenario.swings();
        if (swings.isEmpty()) {
            return List.of();
        }

        final List<BarLabel> labels = new ArrayList<>();
        for (int index = 0; index < swings.size(); index++) {
            final ElliottSwing swing = swings.get(index);
            final boolean highPivot = swing.isRising();
            labels.add(new BarLabel(swing.toIndex(), offsetLabelValue(series, swing.toPrice(), highPivot),
                    waveLabelForPhase(scenario.currentPhase(), index), placementForPivot(highPivot), labelColor,
                    ElliottWaveBtcMacroCycleDemo.WAVE_LABEL_FONT_SCALE));
        }
        return List.copyOf(labels);
    }

    static double interpolateOverlayPrice(final double fromPrice, final double toPrice, final double progress) {
        final double clampedProgress = clamp(progress, 0.0, 1.0);
        if (!Double.isFinite(fromPrice) || !Double.isFinite(toPrice) || fromPrice <= 0.0 || toPrice <= 0.0) {
            return fromPrice + ((toPrice - fromPrice) * clampedProgress);
        }
        final double logFrom = Math.log(fromPrice);
        final double logTo = Math.log(toPrice);
        return Math.exp(logFrom + ((logTo - logFrom) * clampedProgress));
    }

    static String formatInvalidationCondition(final ElliottScenario scenario, final Num value) {
        if (scenario == null || value == null) {
            return "";
        }
        if (scenario.hasKnownDirection()) {
            return (scenario.isBullish() ? "<= " : ">= ") + formatNum(value);
        }
        return "= " + formatNum(value);
    }

    private static String orthodoxWaveFiveTargetRange(final ElliottWaveAnalysisResult.CurrentPhaseAssessment primary) {
        if (primary == null || primary.currentPhase() != ElliottPhase.WAVE4) {
            return "";
        }
        final ElliottScenario scenario = primary.scenario();
        if (scenario == null || !scenario.hasKnownDirection() || !scenario.isBullish()) {
            return "";
        }
        final List<ElliottSwing> swings = scenario.swings();
        if (swings.size() < 4) {
            return "";
        }
        final ElliottSwing wave1 = swings.get(0);
        final ElliottSwing wave3 = swings.get(2);
        final ElliottSwing wave4 = swings.get(3);
        final double waveZeroLow = wave1.fromPrice().doubleValue();
        final double waveOneHigh = wave1.toPrice().doubleValue();
        final double waveThreeHigh = wave3.toPrice().doubleValue();
        final double waveFourLow = wave4.toPrice().doubleValue();
        if (!Double.isFinite(waveZeroLow) || !Double.isFinite(waveOneHigh) || !Double.isFinite(waveThreeHigh)
                || !Double.isFinite(waveFourLow)) {
            return "";
        }
        final double waveOneLength = waveOneHigh - waveZeroLow;
        final double oneToThreeLength = waveThreeHigh - waveZeroLow;
        if (waveOneLength <= 0.0 || oneToThreeLength <= 0.0) {
            return "";
        }
        final double orthodoxLow = waveFourLow + (waveOneLength * 0.618);
        final double orthodoxEqualWaveOne = waveFourLow + waveOneLength;
        final double orthodoxExtended = waveFourLow + (oneToThreeLength * 0.618);
        final double lowerBound = Math.min(orthodoxLow, Math.min(orthodoxEqualWaveOne, orthodoxExtended));
        final double upperBound = Math.max(orthodoxLow, Math.max(orthodoxEqualWaveOne, orthodoxExtended));
        return formatPrice(lowerBound) + " to " + formatPrice(upperBound);
    }

    private static Optional<Path> saveLiveCurrentCycleChart(final BarSeries series,
            final CurrentCycleAnalysis currentCycle, final Path chartDirectory, final String chartFileName) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(currentCycle, "currentCycle");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        Objects.requireNonNull(chartFileName, "chartFileName");
        if (currentCycle.primaryFit() == null) {
            return Optional.empty();
        }

        final ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        final JFreeChart chart = renderLiveCurrentCycleChart(series, currentCycle);
        return chartWorkflow.saveChartImage(chart, series, chartFileName,
                ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT);
    }

    private static LivePresetLegacyView generateLivePresetLegacyView(final BarSeries series,
            final String scenarioSeriesToken, final CurrentCycleAnalysis currentCycle,
            final ElliottWaveBtcMacroCycleDemo.LivePresetReport report, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(scenarioSeriesToken, "scenarioSeriesToken");
        Objects.requireNonNull(currentCycle, "currentCycle");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> displayCandidates = currentCycle
                .displayCandidates();
        final List<LivePresetScenarioView> scenarioViews = buildLivePresetScenarioViews(scenarioSeriesToken,
                displayCandidates);
        saveLegacyCompatibleScenarioCharts(series, scenarioViews, chartDirectory, ElliottDegree.CYCLE);
        return new LivePresetLegacyView(report, scenarioViews);
    }

    private static List<LivePresetScenarioView> buildLivePresetScenarioViews(final String seriesToken,
            final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        final double totalScore = candidates.stream()
                .mapToDouble(ElliottWaveAnalysisResult.CurrentCycleCandidate::totalScore)
                .sum();
        final List<LivePresetScenarioView> scenarioViews = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate = candidates.get(index);
            final double probability = totalScore <= ElliottWaveBtcMacroCycleDemo.EPSILON ? 0.0
                    : candidate.totalScore() / totalScore;
            final String label = index == 0 ? "BASE CASE" : "ALTERNATIVE " + index;
            final String fileName = index == 0 ? livePresetChartBaseCaseFileName(seriesToken)
                    : livePresetChartAlternativeFileName(seriesToken, index);
            scenarioViews.add(new LivePresetScenarioView(label, fileName, probability, candidate));
        }
        return List.copyOf(scenarioViews);
    }

    private static void saveLegacyCompatibleScenarioCharts(final BarSeries series,
            final List<LivePresetScenarioView> scenarioViews, final Path chartDirectory, final ElliottDegree degree) {
        if (scenarioViews.isEmpty()) {
            return;
        }

        final ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        final boolean isHeadless = GraphicsEnvironment.isHeadless();
        final String trendLabel = formatLegacyTrendLabel(scenarioViews);
        for (final LivePresetScenarioView scenarioView : scenarioViews) {
            final ChartPlan plan = buildLegacyCompatibleLiveScenarioPlan(series, scenarioView, trendLabel, degree);
            if (!isHeadless) {
                chartWorkflow.display(plan, buildLegacyScenarioWindowTitle(degree, trendLabel, scenarioView.label(),
                        scenarioView.candidate().fit().scenario(), series.getName()));
            }
            chartWorkflow.save(plan, scenarioView.fileName(), ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH,
                    ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT);
        }
    }

    private static String formatLegacyTrendLabel(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "TREND: UNKNOWN";
        }

        final long bullishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> view.candidate().fit().scenario().isBullish())
                .count();
        final long bearishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> !view.candidate().fit().scenario().isBullish())
                .count();
        if (bullishCount == bearishCount) {
            return "TREND: NEUTRAL";
        }
        return bullishCount > bearishCount ? "TREND: BULLISH" : "TREND: BEARISH";
    }

    private static ChartPlan buildLegacyCompatibleLiveScenarioPlan(final BarSeries series,
            final LivePresetScenarioView scenarioView, final String trendLabel, final ElliottDegree degree) {
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = scenarioView.candidate().fit();
        final Color scenarioColor = fit.scenario().isBullish() ? ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR
                : ElliottWaveBtcMacroCycleDemo.BEARISH_WAVE_COLOR;
        final BarSeriesLabelIndicator labels = new BarSeriesLabelIndicator(series,
                buildWaveLabelsFromScenario(series, fit.scenario(), scenarioColor));
        final FixedIndicator<Num> scenarioPath = buildScenarioIndicator(series, fit.scenario(), fit.countLabel());
        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        return chartWorkflow.builder()
                .withTitle(buildLegacyScenarioTitle(degree, series, trendLabel, scenarioView.label(), fit.scenario()))
                .withSeries(series)
                .withIndicatorOverlay(scenarioPath)
                .withLineColor(scenarioColor)
                .withLineWidth(2.4f)
                .withOpacity(0.90f)
                .withLabel(fit.countLabel())
                .withIndicatorOverlay(labels)
                .withLineColor(Color.WHITE)
                .withLineWidth(2.2f)
                .withOpacity(0.95f)
                .withLabel("Wave pivots")
                .toPlan();
    }

    private static void logLegacyCompatibleLivePreset(final LivePresetLegacyView legacyView) {
        Objects.requireNonNull(legacyView, "legacyView");

        final ElliottWaveBtcMacroCycleDemo.LivePresetReport report = legacyView.report();
        final CurrentCycleSummary summary = report.currentCycle();
        final List<LivePresetScenarioView> scenarioViews = legacyView.scenarioViews();

        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", summarizeLegacyScenarioViews(scenarioViews));
        LOG.info("Strong consensus: {} | Consensus phase: {}", hasStrongConsensus(scenarioViews),
                consensusPhase(scenarioViews).map(Enum::name).orElse("NONE"));
        LOG.info("Trend bias: {}", formatLegacyTrendLabel(scenarioViews));
        LOG.info("Historical status: {}", summary.historicalStatus());
        if (!scenarioViews.isEmpty()) {
            final LivePresetScenarioView baseCase = scenarioViews.getFirst();
            final ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = baseCase.candidate().fit();
            final ElliottScenario scenario = fit.scenario();
            final ElliottConfidence confidence = scenario.confidence();
            LOG.info("BASE CASE SCENARIO: {} ({})", scenario.currentPhase(), scenario.type());
            LOG.info("  Overall confidence: {}% ({})",
                    String.format(java.util.Locale.ROOT, "%.1f", confidence.asPercentage()),
                    confidence.isHighConfidence() ? "HIGH" : confidence.isLowConfidence() ? "LOW" : "MEDIUM");
            LOG.info("  Scenario probability: raw={}%, calibrated={}%",
                    String.format(java.util.Locale.ROOT, "%.1f", baseCase.probability() * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f", baseCase.probability() * 100.0));
            LOG.info("  Factor scores: Fibonacci={}% | Time={}% | Alternation={}% | Channel={}% | Completeness={}%",
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.fibonacciScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.timeProportionScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.alternationScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.channelScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.completenessScore()) * 100.0));
            LOG.info("  Primary reason: {}", confidence.primaryReason());
            LOG.info("  Weakest factor: {}", confidence.weakestFactor());
            LOG.info("  Direction: {} | Phase invalidation: {} | Structural invalidation: {} | Target: {}",
                    scenario.hasKnownDirection() ? (scenario.isBullish() ? "BULLISH" : "BEARISH") : "UNKNOWN",
                    formatInvalidationCondition(scenario, fit.phaseInvalidationPrice()),
                    formatInvalidationCondition(scenario, fit.invalidationPrice()), scenario.primaryTarget());
        }

        if (scenarioViews.size() > 1) {
            LOG.info("ALTERNATIVE SCENARIOS ({}):", scenarioViews.size() - 1);
            for (int index = 1; index < scenarioViews.size(); index++) {
                final LivePresetScenarioView alternative = scenarioViews.get(index);
                final ElliottScenario scenario = alternative.candidate().fit().scenario();
                LOG.info("  {}. {} ({}) - {}% confidence | raw={}%, calibrated={}%", index, scenario.currentPhase(),
                        scenario.type(),
                        String.format(java.util.Locale.ROOT, "%.1f", scenario.confidence().asPercentage()),
                        String.format(java.util.Locale.ROOT, "%.1f", alternative.probability() * 100.0),
                        String.format(java.util.Locale.ROOT, "%.1f", alternative.probability() * 100.0));
            }
        }
        LOG.info(
                "Current macro read: primary={} | alternate={} | currentWave={} | phase invalidation {} | structural invalidation {} | orthodox wave5 target {}",
                summary.primaryCount(), summary.alternateCount(), summary.currentWave(), summary.invalidationPrice(),
                summary.structuralInvalidationPrice(), summary.orthodoxWaveFiveTargetRange());
        LOG.info("Macro summary JSON: {}", report.summaryPath());
        LOG.info("Macro current-cycle chart: {}", report.chartPath());
        LOG.info("======================================");
    }

    private static String summarizeLegacyScenarioViews(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "No scenarios";
        }
        final LivePresetScenarioView baseCase = scenarioViews.getFirst();
        final StringBuilder summary = new StringBuilder();
        summary.append(scenarioViews.size())
                .append(" scenario(s): Base case=")
                .append(baseCase.candidate().fit().scenario().currentPhase())
                .append(" (")
                .append(String.format(java.util.Locale.ROOT, "%.1f%%",
                        baseCase.candidate().fit().scenario().confidence().asPercentage()))
                .append(")");
        if (scenarioViews.size() > 1) {
            summary.append(", ").append(scenarioViews.size() - 1).append(" alternative(s)");
        }
        consensusPhase(scenarioViews).ifPresent(phase -> summary.append(", consensus=").append(phase));
        return summary.toString();
    }

    private static boolean hasStrongConsensus(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return false;
        }
        if (scenarioViews.size() == 1) {
            return true;
        }
        final double spread = scenarioViews.getFirst().probability() - scenarioViews.get(1).probability();
        return spread >= 0.08;
    }

    private static Optional<ElliottPhase> consensusPhase(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return Optional.empty();
        }

        final Map<ElliottPhase, Double> phaseWeights = new LinkedHashMap<>();
        for (final LivePresetScenarioView scenarioView : scenarioViews) {
            final ElliottScenario scenario = scenarioView.candidate().fit().scenario();
            phaseWeights.merge(scenario.currentPhase(), scenarioView.probability(), Double::sum);
        }
        final Map.Entry<ElliottPhase, Double> bestPhase = phaseWeights.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (bestPhase == null || bestPhase.getValue() < 0.60) {
            return Optional.empty();
        }
        return Optional.of(bestPhase.getKey());
    }

    private static String buildLegacyScenarioTitle(final ElliottDegree degree, final BarSeries series,
            final String trendLabel, final String scenarioLabel, final ElliottScenario scenario) {
        return String.format(java.util.Locale.ROOT, "Elliott Wave (%s) - %s - %s - %s: %s (%s) - %.1f%% confidence",
                degree, series.getName(), trendLabel, scenarioLabel, scenario.currentPhase(), scenario.type(),
                scenario.confidence().asPercentage());
    }

    private static String buildLegacyScenarioWindowTitle(final ElliottDegree degree, final String trendLabel,
            final String scenarioLabel, final ElliottScenario scenario, final String seriesName) {
        return String.format(java.util.Locale.ROOT, "%s - %s - %s: %s (%s) - %.1f%% - %s", degree, trendLabel,
                scenarioLabel, scenario.currentPhase(), scenario.type(), scenario.confidence().asPercentage(),
                seriesName);
    }

    private static String livePresetChartBaseCaseFileName(final String seriesToken) {
        return "elliott-wave-analysis-" + seriesToken + "-cycle-base-case";
    }

    private static String livePresetChartAlternativeFileName(final String seriesToken, final int alternativeIndex) {
        return "elliott-wave-analysis-" + seriesToken + "-cycle-alternative-" + alternativeIndex;
    }

    private static String liveCurrentCycleChartFileName(final String seriesToken) {
        return "elliott-wave-" + seriesToken + "-live-macro-current-cycle";
    }

    private static String liveSummaryFileName(final String seriesToken) {
        return "elliott-wave-" + seriesToken + "-live-macro-current-cycle-summary.json";
    }

    private static String scenarioSeriesName(final BarSeries series) {
        final String rawName = series == null || series.getName() == null ? "series" : series.getName().trim();
        final String normalized = rawName.isEmpty() ? "series"
                : rawName.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+", "")
                        .replaceAll("-+$", "");
        return normalized.isBlank() ? "series" : normalized;
    }

    private static JFreeChart renderLiveCurrentCycleChart(final BarSeries series,
            final CurrentCycleAnalysis currentCycle) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(currentCycle, "currentCycle");

        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment primaryFit = currentCycle.primaryFit();
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment alternateFit = currentCycle.alternateFit();
        final BarSeriesLabelIndicator primaryLabels = primaryFit == null
                ? new BarSeriesLabelIndicator(series, List.of())
                : new BarSeriesLabelIndicator(series, buildWaveLabelsFromScenario(series, primaryFit.scenario(),
                        ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR));
        final FixedIndicator<Num> primaryPath = primaryFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().primaryCount())
                : buildScenarioIndicator(series, primaryFit.scenario(), currentCycle.summary().primaryCount());
        final FixedIndicator<Num> alternatePath = alternateFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().alternateCount())
                : buildScenarioIndicator(series, alternateFit.scenario(), currentCycle.summary().alternateCount());

        final String chartTitle = series.getName() + " live macro current cycle";
        final ChartPlan plan;
        if (alternateFit != null && !currentCycle.summary().alternateCount().isBlank()) {
            plan = chartWorkflow.builder()
                    .withTitle(chartTitle)
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(alternatePath)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_CANDIDATE_COLOR)
                    .withLineWidth(2.0f)
                    .withOpacity(0.55f)
                    .withLabel(currentCycle.summary().alternateCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        } else {
            plan = chartWorkflow.builder()
                    .withTitle(chartTitle)
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        }
        final JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    private static double safeConfidenceScore(final double score) {
        return Double.isFinite(score) ? clamp(score, 0.0, 1.0) : 0.0;
    }

    private static double safeConfidenceScore(final Num score) {
        if (score == null || score.isNaN()) {
            return 0.0;
        }
        return safeConfidenceScore(score.doubleValue());
    }

    private static FixedIndicator<Num> buildScenarioIndicator(final BarSeries series, final ElliottScenario scenario,
            final String label) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        applyScenarioPath(values, series, scenario);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static FixedIndicator<Num> emptyScenarioIndicator(final BarSeries series, final String label) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    static void applyLogPriceAxis(final JFreeChart chart, final BarSeries series) {
        if (!(chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot)) {
            return;
        }
        if (combinedPlot.getSubplots() == null || combinedPlot.getSubplots().isEmpty()) {
            return;
        }

        final Object subplot = combinedPlot.getSubplots().getFirst();
        if (!(subplot instanceof XYPlot pricePlot)) {
            return;
        }

        final LogAxis logAxis = new LogAxis("Price (USD, log)");
        logAxis.setAutoRange(true);
        logAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        logAxis.setLabelPaint(Color.LIGHT_GRAY);
        logAxis.setSmallestValue(smallestPositiveLow(series));
        pricePlot.setRangeAxis(logAxis);
    }

    private static Optional<Path> saveHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final MacroStudy study,
            final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(study, "study");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        final JFreeChart chart = renderHistoricalChart(series, registry, study);
        return chartWorkflow.saveChartImage(chart, series, ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_FILE_NAME,
                ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT);
    }

    static JFreeChart renderHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final MacroStudy study) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(study, "study");

        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        final List<SegmentScenarioFit> segmentFits = study.selectedProfile().chartSegments();
        final boolean useStudySegments = !segmentFits.isEmpty();
        final List<LegSegment> legSegments = useStudySegments ? buildChartLegSegments(segmentFits)
                : buildLegSegments(registry);
        final int currentCycleStartIndex = useStudySegments ? latestBottomAnchorIndex(series, segmentFits)
                : latestBottomAnchorIndex(series, registry);
        final BarSeriesLabelIndicator anchorLabels = new BarSeriesLabelIndicator(series,
                useStudySegments ? buildChartEndpointLabels(series, segmentFits) : buildAnchorLabels(series, registry));
        final BarSeriesLabelIndicator waveLabels = new BarSeriesLabelIndicator(series,
                buildSegmentWaveLabels(series, segmentFits));
        final FixedIndicator<Num> bullishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, true, true,
                "Bullish accepted wave segments");
        final FixedIndicator<Num> bearishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, false, true,
                "Bearish accepted wave segments");
        final FixedIndicator<Num> bullishFallbackFits = buildScenarioFitIndicator(series, segmentFits, true, false,
                "Bullish fallback wave segments");
        final FixedIndicator<Num> bearishFallbackFits = buildScenarioFitIndicator(series, segmentFits, false, false,
                "Bearish fallback wave segments");
        final FixedIndicator<Num> bullishLegs = buildCycleLegIndicator(series, legSegments, true, "Bullish 1-2-3-4-5",
                currentCycleStartIndex);
        final FixedIndicator<Num> bearishLegs = buildCycleLegIndicator(series, legSegments, false, "Bearish A-B-C",
                currentCycleStartIndex);
        final ChartPlan plan = chartWorkflow.builder()
                .withTitle(series.getName() + " macro cycles: bullish 1-5 tops and bearish A-C lows")
                .withSeries(series)
                .withIndicatorOverlay(bullishLegs)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR)
                .withLineWidth(4.2f)
                .withOpacity(0.60f)
                .withLabel("Bullish 1-2-3-4-5")
                .withIndicatorOverlay(bearishLegs)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR)
                .withLineWidth(4.2f)
                .withOpacity(0.60f)
                .withLabel("Bearish A-B-C")
                .withIndicatorOverlay(bullishAcceptedFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                .withLineWidth(2.4f)
                .withOpacity(0.72f)
                .withLabel("Bullish accepted wave segments")
                .withIndicatorOverlay(bearishAcceptedFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BEARISH_WAVE_COLOR)
                .withLineWidth(2.4f)
                .withOpacity(0.72f)
                .withLabel("Bearish accepted wave segments")
                .withIndicatorOverlay(bullishFallbackFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_CANDIDATE_COLOR)
                .withLineWidth(1.8f)
                .withOpacity(0.56f)
                .withLabel("Bullish fallback wave segments")
                .withIndicatorOverlay(bearishFallbackFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BEARISH_CANDIDATE_COLOR)
                .withLineWidth(1.8f)
                .withOpacity(0.56f)
                .withLabel("Bearish fallback wave segments")
                .withIndicatorOverlay(anchorLabels)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.ANCHOR_OVERLAY_COLOR)
                .withLineWidth(1.5f)
                .withOpacity(0.55f)
                .withLabel("Macro-cycle anchors")
                .withIndicatorOverlay(waveLabels)
                .withLineColor(Color.WHITE)
                .withLineWidth(2.5f)
                .withOpacity(0.95f)
                .withLabel("Matched Elliott wave labels")
                .toPlan();
        final JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    private static List<LegSegment> buildChartLegSegments(final List<SegmentScenarioFit> segmentFits) {
        final LinkedHashMap<String, LegSegment> segments = new LinkedHashMap<>();
        for (final SegmentScenarioFit fit : segmentFits) {
            segments.putIfAbsent(segmentKey(fit.segment()), fit.segment());
        }
        return List.copyOf(segments.values());
    }

    private static List<BarLabel> buildChartEndpointLabels(final BarSeries series,
            final List<SegmentScenarioFit> segmentFits) {
        final LinkedHashMap<String, ElliottWaveAnchorCalibrationHarness.Anchor> anchors = new LinkedHashMap<>();
        final Num topPad = series.numFactory().numOf("1.02");
        final Num lowPad = series.numFactory().numOf("0.98");
        for (final SegmentScenarioFit fit : segmentFits) {
            anchors.putIfAbsent(fit.segment().fromAnchor().id(), fit.segment().fromAnchor());
            anchors.putIfAbsent(fit.segment().toAnchor().id(), fit.segment().toAnchor());
        }
        return anchors.values()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .map(anchor -> {
                    final int index = nearestIndex(series, anchor.at());
                    final Bar bar = series.getBar(index);
                    final boolean top = anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
                    final Num yValue = top ? bar.getHighPrice().multipliedBy(topPad)
                            : bar.getLowPrice().multipliedBy(lowPad);
                    final String date = bar.getEndTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
                    final String text = top ? "Bullish 1-5 top\n" + date : "Bearish A-C low\n" + date;
                    final LabelPlacement placement = top ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
                    final Color labelColor = top ? ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR
                            : ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR;
                    return new BarLabel(index, yValue, text, placement, labelColor);
                })
                .toList();
    }

    private static MacroProfileEvaluation evaluateProfile(final BarSeries series, final MacroLogicProfile profile,
            final List<LegSegment> chartLegs, final List<MacroCycle> historicalCycles) {
        final ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        final List<SegmentScenarioFit> chartSegments = new ArrayList<>();
        final Map<String, SegmentScenarioFit> segmentById = new LinkedHashMap<>();
        for (final LegSegment legSegment : chartLegs) {
            fitSegment(series, legSegment, profile, profileRunner).ifPresent(fit -> {
                chartSegments.add(fit);
                segmentById.put(segmentKey(legSegment), fit);
            });
        }

        final List<CycleFit> cycleFits = new ArrayList<>();
        for (final MacroCycle cycle : historicalCycles) {
            final SegmentScenarioFit bullishFit = segmentById.get(segmentKey(cycle.bullishLeg()));
            final SegmentScenarioFit bearishFit = segmentById.get(segmentKey(cycle.bearishLeg()));
            cycleFits.add(CycleFit.create(cycle, bullishFit, bearishFit));
        }

        int acceptedCycles = 0;
        int acceptedSegments = 0;
        double scoreTotal = 0.0;
        for (final CycleFit cycleFit : cycleFits) {
            if (cycleFit.accepted()) {
                acceptedCycles++;
            }
            if (cycleFit.bullishFit() != null && cycleFit.bullishFit().accepted()) {
                acceptedSegments++;
            }
            if (cycleFit.bearishFit() != null && cycleFit.bearishFit().accepted()) {
                acceptedSegments++;
            }
            scoreTotal += cycleFit.aggregateScore();
        }
        final double aggregateScore = cycleFits.isEmpty() ? 0.0 : scoreTotal / cycleFits.size();
        final boolean historicalFitPassed = !cycleFits.isEmpty() && acceptedCycles == cycleFits.size();
        return new MacroProfileEvaluation(profile, aggregateScore, acceptedCycles, acceptedSegments,
                historicalFitPassed, List.copyOf(cycleFits), List.copyOf(chartSegments));
    }

    private static ElliottWaveAnalysisRunner buildProfileRunner(final MacroLogicProfile profile) {
        final int runnerMaxScenarios = Math.max(profile.runnerMaxScenarios(),
                ElliottWaveBtcMacroCycleDemo.MIN_CORE_SEGMENT_SCENARIOS);
        return ElliottWaveAnalysisRunner.builder()
                .degree(profile.runnerDegree())
                .logicProfile(profile.coreLogicProfile())
                .maxScenarios(runnerMaxScenarios)
                .minConfidence(0.0)
                .seriesSelector((inputSeries, ignoredDegree) -> inputSeries)
                .build();
    }

    private static Comparator<MacroProfileEvaluation> profileEvaluationComparator() {
        return Comparator.comparingDouble(MacroProfileEvaluation::aggregateScore)
                .reversed()
                .thenComparingInt(MacroProfileEvaluation::acceptedCycles)
                .reversed()
                .thenComparingInt(MacroProfileEvaluation::acceptedSegments)
                .reversed()
                .thenComparingInt(evaluation -> evaluation.profile().orthodoxyRank());
    }

    private static List<HypothesisResult> buildHypotheses(final List<MacroProfileEvaluation> evaluations) {
        final MacroProfileEvaluation classical = findEvaluation(evaluations, "orthodox-classical");
        final MacroProfileEvaluation hierarchical = findEvaluation(evaluations, "h1-hierarchical-swing");
        final MacroProfileEvaluation relaxedImpulse = findEvaluation(evaluations, "h2-btc-relaxed-impulse");
        final MacroProfileEvaluation relaxedCorrective = findEvaluation(evaluations, "h3-btc-relaxed-corrective");
        final MacroProfileEvaluation anchorFirst = findEvaluation(evaluations, "h4-anchor-first-hybrid");

        return List.of(
                hypothesisFromProfile("H1", "Swing extraction is the main macro bottleneck", hierarchical, classical,
                        "Hierarchical pivots outperform the strict classical candidate extractor on the BTC truth set.",
                        "Hierarchical pivots do not improve the BTC historical fit enough on their own."),
                hypothesisFromProfile("H2", "Rigid impulse rules are too strict for BTC", relaxedImpulse, hierarchical,
                        "Relaxing impulse-only rules improves the historical BTC cycle fit beyond the hierarchical baseline.",
                        "Relaxing impulse-only rules does not materially improve the BTC historical fit."),
                hypothesisFromProfile("H3", "Corrective handling is the missing coverage", relaxedCorrective,
                        relaxedImpulse,
                        "Relaxing corrective handling improves the BTC historical fit beyond impulse-only relaxations.",
                        "Relaxing corrective handling does not materially improve the BTC historical fit."),
                hypothesisFromProfile("H4", "Anchor-first scoring beats phase-first selection", anchorFirst,
                        relaxedCorrective,
                        "The anchor-first hybrid profile is the strongest overall BTC fit and wins the macro study.",
                        "The anchor-first hybrid profile does not beat the best simpler profile on the BTC truth set."));
    }

    private static HypothesisResult hypothesisFromProfile(final String id, final String title,
            final MacroProfileEvaluation candidate, final MacroProfileEvaluation baseline,
            final String supportedSummary, final String rejectedSummary) {
        final boolean supported = candidate.aggregateScore() > baseline.aggregateScore() + 0.01
                || candidate.acceptedCycles() > baseline.acceptedCycles();
        final Map<String, String> evidence = orderedEvidence("candidateProfile", candidate.profile().id(),
                "candidateScore", formatScore(candidate.aggregateScore()), "candidateAcceptedCycles",
                Integer.toString(candidate.acceptedCycles()), "baselineProfile", baseline.profile().id(),
                "baselineScore", formatScore(baseline.aggregateScore()), "baselineAcceptedCycles",
                Integer.toString(baseline.acceptedCycles()));
        return new HypothesisResult(id, title, supported, supported ? supportedSummary : rejectedSummary, evidence);
    }

    private static MacroProfileEvaluation findEvaluation(final List<MacroProfileEvaluation> evaluations,
            final String profileId) {
        return evaluations.stream()
                .filter(evaluation -> evaluation.profile().id().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing profile " + profileId));
    }

    private static Optional<SegmentScenarioFit> fitSegment(final BarSeries series, final LegSegment legSegment,
            final MacroLogicProfile profile, final ElliottWaveAnalysisRunner profileRunner) {
        final int startIndex = nearestIndex(series, legSegment.fromAnchor().at());
        final int endIndex = nearestIndex(series, legSegment.toAnchor().at());
        if (endIndex <= startIndex) {
            return Optional.empty();
        }
        return legSegment.bullish()
                ? fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, true)
                : fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, false);
    }

    private static Optional<SegmentScenarioFit> fitSegmentFromCoreRunner(final BarSeries series,
            final LegSegment legSegment, final MacroLogicProfile profile, final ElliottWaveAnalysisRunner profileRunner,
            final int startIndex, final int endIndex, final boolean bullish) {
        return profileRunner
                .selectAcceptedOrFallbackTerminalLegForWindow(series, startIndex, endIndex, bullish,
                        ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS,
                        Math.max(ElliottWaveBtcMacroCycleDemo.DEFAULT_ACCEPTED_SEGMENT_SCORE,
                                profile.acceptanceThreshold()),
                        0.30, 0.35, 0.80)
                .map(selection -> fitFromCoreAssessment(legSegment, profile, selection.assessment(), bullish,
                        selection.accepted()));
    }

    private static List<BarLabel> buildAnchorLabels(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        final List<BarLabel> labels = new ArrayList<>();
        final Num topPad = series.numFactory().numOf("1.02");
        final Num lowPad = series.numFactory().numOf("0.98");
        for (final ElliottWaveAnchorCalibrationHarness.Anchor anchor : registry.anchors()) {
            final int barIndex = nearestIndex(series, anchor.at());
            final Bar bar = series.getBar(barIndex);
            final boolean top = anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            final Num yValue = top ? bar.getHighPrice().multipliedBy(topPad) : bar.getLowPrice().multipliedBy(lowPad);
            final String date = bar.getEndTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
            final String text = top ? "Bullish 1-5 top\n" + date : "Bearish A-C low\n" + date;
            final LabelPlacement placement = top ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
            final Color labelColor = top ? ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR
                    : ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR;
            labels.add(new BarLabel(barIndex, yValue, text, placement, labelColor));
        }
        return List.copyOf(labels);
    }

    private static List<BarLabel> buildSegmentWaveLabels(final BarSeries series,
            final List<SegmentScenarioFit> segmentFits) {
        final List<BarLabel> rawLabels = new ArrayList<>();
        for (final SegmentScenarioFit fit : segmentFits) {
            final Color labelColor = fit.accepted()
                    ? (fit.bullish() ? ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR
                            : ElliottWaveBtcMacroCycleDemo.BEARISH_WAVE_COLOR)
                    : (fit.bullish() ? ElliottWaveBtcMacroCycleDemo.BULLISH_CANDIDATE_COLOR
                            : ElliottWaveBtcMacroCycleDemo.BEARISH_CANDIDATE_COLOR);
            rawLabels.addAll(buildWaveLabelsFromScenario(series, fit.scenario(), labelColor));
        }
        return deconflictLabels(series, rawLabels);
    }

    private static List<BarLabel> deconflictLabels(final BarSeries series, final List<BarLabel> labels) {
        final List<BarLabel> ordered = labels.stream().sorted(Comparator.comparingInt(BarLabel::barIndex)).toList();
        final List<BarLabel> adjusted = new ArrayList<>();
        for (final BarLabel label : ordered) {
            int clusterDepth = 0;
            for (int index = adjusted.size() - 1; index >= 0; index--) {
                final BarLabel prior = adjusted.get(index);
                if (label.barIndex() - prior.barIndex() > ElliottWaveBtcMacroCycleDemo.LABEL_CLUSTER_BAR_GAP) {
                    break;
                }
                if (prior.placement() == label.placement()) {
                    clusterDepth++;
                }
            }
            final Num adjustedY = applyLabelClusterOffset(series, label, clusterDepth);
            adjusted.add(new BarLabel(label.barIndex(), adjustedY, label.text(), label.placement(), label.color(),
                    label.fontScale()));
        }
        return List.copyOf(adjusted);
    }

    private static Num applyLabelClusterOffset(final BarSeries series, final BarLabel label, final int clusterDepth) {
        if (clusterDepth == 0) {
            return label.yValue();
        }
        final double step = 0.04 * clusterDepth;
        final double multiplier = switch (label.placement()) {
        case ABOVE -> 1.0 + step;
        case BELOW -> 1.0 - step;
        case CENTER -> 1.0;
        };
        return label.yValue().multipliedBy(series.numFactory().numOf(multiplier));
    }

    private static FixedIndicator<Num> buildScenarioFitIndicator(final BarSeries series,
            final List<SegmentScenarioFit> segmentFits, final boolean bullish, final boolean accepted,
            final String label) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        for (final SegmentScenarioFit fit : segmentFits) {
            if (fit.bullish() != bullish || fit.accepted() != accepted) {
                continue;
            }
            applyScenarioPath(values, series, fit.scenario());
        }
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static void applyScenarioPath(final Num[] values, final BarSeries series, final ElliottScenario scenario) {
        for (final ElliottSwing swing : scenario.swings()) {
            final int fromIndex = Math.max(series.getBeginIndex(), Math.min(swing.fromIndex(), swing.toIndex()));
            final int toIndex = Math.min(series.getEndIndex(), Math.max(swing.fromIndex(), swing.toIndex()));
            if (toIndex < fromIndex) {
                continue;
            }
            final double fromPrice = swing.fromPrice().doubleValue();
            final double toPrice = swing.toPrice().doubleValue();
            final int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                final double progress = (double) (index - fromIndex) / length;
                final double interpolated = interpolateOverlayPrice(fromPrice, toPrice, progress);
                values[index] = series.numFactory().numOf(interpolated);
            }
        }
    }

    private static FixedIndicator<Num> buildCycleLegIndicator(final BarSeries series,
            final List<LegSegment> legSegments, final boolean bullish, final String label,
            final int currentCycleStartIndex) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        for (final LegSegment legSegment : legSegments) {
            if (legSegment.bullish() != bullish) {
                continue;
            }
            final ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor = legSegment.fromAnchor();
            final ElliottWaveAnchorCalibrationHarness.Anchor toAnchor = legSegment.toAnchor();
            final int fromIndex = nearestIndex(series, fromAnchor.at());
            final int toIndex = nearestIndex(series, toAnchor.at());
            if (toIndex < fromIndex) {
                continue;
            }

            final double fromPrice = anchorPrice(series, fromIndex, fromAnchor.type());
            final double toPrice = anchorPrice(series, toIndex, toAnchor.type());
            final int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                final double progress = (double) (index - fromIndex) / length;
                final double interpolated = fromPrice + ((toPrice - fromPrice) * progress);
                values[index] = series.numFactory().numOf(interpolated);
            }
        }
        clipCurrentCycleMacroGuide(values, currentCycleStartIndex);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static void clipCurrentCycleMacroGuide(final Num[] values, final int currentCycleStartIndex) {
        if (currentCycleStartIndex == Integer.MAX_VALUE) {
            return;
        }
        final int clipFromIndex = Math.max(0, currentCycleStartIndex + 1);
        for (int index = clipFromIndex; index < values.length; index++) {
            values[index] = NaN;
        }
    }

    private static List<LegSegment> buildLegSegments(
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = registry.anchors()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .toList();
        final List<LegSegment> legSegments = new ArrayList<>();
        for (int index = 1; index < anchors.size(); index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor = anchors.get(index - 1);
            final ElliottWaveAnchorCalibrationHarness.Anchor toAnchor = anchors.get(index);
            if (fromAnchor.type() == toAnchor.type()) {
                continue;
            }
            final boolean bullish = fromAnchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM
                    && toAnchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            legSegments.add(new LegSegment(fromAnchor, toAnchor, bullish));
        }
        return List.copyOf(legSegments);
    }

    private static List<MacroCycle> buildHistoricalCycles(
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = registry.anchors()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .toList();
        final List<MacroCycle> cycles = new ArrayList<>();
        for (int index = 0; index <= anchors.size() - 3; index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor start = anchors.get(index);
            final ElliottWaveAnchorCalibrationHarness.Anchor peak = anchors.get(index + 1);
            final ElliottWaveAnchorCalibrationHarness.Anchor low = anchors.get(index + 2);
            if (start.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM
                    || peak.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.TOP
                    || low.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM) {
                continue;
            }
            final String partition = low.partition().name().toLowerCase();
            final LegSegment bullishLeg = new LegSegment(start, peak, true);
            final LegSegment bearishLeg = new LegSegment(peak, low, false);
            cycles.add(new MacroCycle(start.id() + "->" + peak.id() + "->" + low.id(), partition, start, peak, low,
                    bullishLeg, bearishLeg));
        }
        return List.copyOf(cycles);
    }

    private static String segmentKey(final LegSegment legSegment) {
        return legSegment.fromAnchor().id() + "->" + legSegment.toAnchor().id();
    }

    private static int latestBottomAnchorIndex(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return registry.anchors()
                .stream()
                .filter(anchor -> anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM)
                .max(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .map(anchor -> nearestIndex(series, anchor.at()))
                .orElse(Integer.MAX_VALUE);
    }

    private static int latestBottomAnchorIndex(final BarSeries series, final List<SegmentScenarioFit> segmentFits) {
        return segmentFits.stream()
                .flatMap(fit -> java.util.stream.Stream.of(fit.segment().fromAnchor(), fit.segment().toAnchor()))
                .filter(anchor -> anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM)
                .max(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .map(anchor -> nearestIndex(series, anchor.at()))
                .orElse(Integer.MAX_VALUE);
    }

    private static double anchorPrice(final BarSeries series, final int barIndex,
            final ElliottWaveAnchorCalibrationHarness.AnchorType anchorType) {
        final Bar bar = series.getBar(barIndex);
        return anchorType == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? bar.getHighPrice().doubleValue()
                : bar.getLowPrice().doubleValue();
    }

    private static String waveLabelForPhase(final ElliottPhase phase, final int waveIndex) {
        if (phase.isImpulse()) {
            return String.valueOf(waveIndex + 1);
        }
        if (phase.isCorrective()) {
            return switch (waveIndex) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
            };
        }
        return String.valueOf(waveIndex + 1);
    }

    private static LabelPlacement placementForPivot(final boolean highPivot) {
        return highPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    private static Num offsetLabelValue(final BarSeries series, final Num pivotPrice, final boolean highPivot) {
        final Num multiplier = highPivot ? series.numFactory().numOf("1.02") : series.numFactory().numOf("0.98");
        return pivotPrice.multipliedBy(multiplier);
    }

    private static int nearestIndex(final BarSeries series, final Instant target) {
        int nearest = series.getBeginIndex();
        long bestDistance = Long.MAX_VALUE;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            final long distance = Math.abs(series.getBar(index).getEndTime().toEpochMilli() - target.toEpochMilli());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static double smallestPositiveLow(final BarSeries series) {
        double smallest = Double.POSITIVE_INFINITY;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            final double low = series.getBar(index).getLowPrice().doubleValue();
            if (Double.isFinite(low) && low > 0.0 && low < smallest) {
                smallest = low;
            }
        }
        return Double.isFinite(smallest) ? smallest : 1.0;
    }

    private static String formatScore(final double score) {
        return String.format(java.util.Locale.ROOT, "%.3f", score);
    }

    private static String formatNum(final Num value) {
        return value == null ? "" : value.toString();
    }

    private static String formatPrice(final double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static Map<String, String> orderedEvidence(final String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must contain complete key/value pairs");
        }
        final Map<String, String> ordered = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            ordered.put(entries[index], entries[index + 1]);
        }
        return Map.copyOf(ordered);
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void saveSummary(final String json, final Path summaryPath, final String description) {
        try {
            Files.createDirectories(summaryPath.getParent());
            Files.writeString(summaryPath, json);
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to write " + description + " " + summaryPath, exception);
        }
    }

    record CanonicalStructure(Optional<MacroStudy> historicalStudy, CurrentCycleAnalysis currentCycle) {

        CanonicalStructure {
            historicalStudy = historicalStudy == null ? Optional.empty() : historicalStudy;
            Objects.requireNonNull(currentCycle, "currentCycle");
        }
    }

    private record LivePresetExecution(CurrentCycleAnalysis currentCycle,
            ElliottWaveBtcMacroCycleDemo.LivePresetReport report) {

        LivePresetExecution {
            Objects.requireNonNull(currentCycle, "currentCycle");
            Objects.requireNonNull(report, "report");
        }
    }

    private record LivePresetLegacyView(ElliottWaveBtcMacroCycleDemo.LivePresetReport report,
            List<LivePresetScenarioView> scenarioViews) {

        LivePresetLegacyView {
            Objects.requireNonNull(report, "report");
            scenarioViews = scenarioViews == null ? List.of() : List.copyOf(scenarioViews);
        }
    }

    private record LivePresetScenarioView(String label, String fileName, double probability,
            ElliottWaveAnalysisResult.CurrentCycleCandidate candidate) {

        LivePresetScenarioView {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(candidate, "candidate");
        }
    }
}

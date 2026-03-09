/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.ta4j.core.num.NaN.NaN;

import java.awt.Color;
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
        final MacroStudy study = evaluateMacroStudy(series, registry);
        final Optional<Path> chartPath = saveHistoricalChart(series, registry, study, chartDirectory);
        final String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        final Path summaryPath = chartDirectory.resolve(ElliottWaveBtcMacroCycleDemo.DEFAULT_SUMMARY_FILE_NAME)
                .toAbsolutePath()
                .normalize();
        final String baselineProfileId = ElliottWaveWalkForwardProfiles.baseline()
                .metadata()
                .getOrDefault("profile", "baseline-minute-f2-h2l2-max25-sw0");
        final CurrentCycleSummary currentCycle = study.currentCycle().withChartPath(chartPathText);
        final DemoReport report = new DemoReport(registry.version(), registry.datasetResource(), baselineProfileId,
                study.selectedProfile().profile().id(), study.selectedProfile().profile().hypothesisId(),
                study.selectedProfile().historicalFitPassed(),
                "Macro-cycle decomposition selected from core-ranked anchor-to-anchor wave fits", chartPathText,
                summaryPath.toString(), study.profileScores(), study.cycles(), study.hypotheses(), currentCycle);
        saveSummary(report.toJson(), summaryPath, "macro-cycle summary");
        return report;
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
                currentCycle.summary(), currentCycle.primaryFit(), currentCycle.alternateFit());
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
                primary == null ? 0.0 : primary.fitScore(), alternate == null ? 0.0 : alternate.fitScore(), "");
        return new CurrentCycleAnalysis(summary, primary, alternate, assessment.candidates(),
                assessment.distinctCandidates(5));
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
            final int startIndex, final int endIndex) {
        final ElliottScenario scenario = assessment.scenario();
        final double structureScore = assessment.structureScore();
        final double ruleScore = assessment.ruleScore();
        final double spacingScore = assessment.spacingScore();
        final double strengthScore = assessment.strengthScore();
        final double fitScore = assessment.fitScore();
        final boolean accepted = assessment.passesAnchoredWindowAcceptance(startIndex, endIndex,
                Math.max(ElliottWaveBtcMacroCycleDemo.DEFAULT_ACCEPTED_SEGMENT_SCORE, profile.acceptanceThreshold()),
                0.30, 0.35, 0.80, ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS);
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

    private static JFreeChart renderHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final MacroStudy study) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(study, "study");

        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        final List<LegSegment> legSegments = buildLegSegments(registry);
        final List<SegmentScenarioFit> segmentFits = study.selectedProfile().chartSegments();
        final int currentCycleStartIndex = latestBottomAnchorIndex(series, registry);
        final BarSeriesLabelIndicator anchorLabels = new BarSeriesLabelIndicator(series,
                buildAnchorLabels(series, registry));
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
        final ElliottWaveAnalysisResult analysis = profileRunner.analyzeWindow(series, startIndex, endIndex);
        final ScenarioType expectedType = bullish ? ScenarioType.IMPULSE : null;
        final ElliottPhase expectedPhase = bullish ? ElliottPhase.WAVE5 : ElliottPhase.CORRECTIVE_C;
        final int expectedWaveCount = bullish ? 5 : 3;
        final Boolean expectedDirection = bullish ? Boolean.TRUE : Boolean.FALSE;
        return analysis
                .recommendedAcceptedOrFallbackBaseScenarioForWindow(series, startIndex, endIndex, expectedType,
                        expectedPhase, expectedWaveCount, expectedDirection,
                        ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS,
                        Math.max(ElliottWaveBtcMacroCycleDemo.DEFAULT_ACCEPTED_SEGMENT_SCORE,
                                profile.acceptanceThreshold()),
                        0.30, 0.35, 0.80)
                .map(assessment -> fitFromCoreAssessment(legSegment, profile, assessment, bullish, startIndex,
                        endIndex));
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
}

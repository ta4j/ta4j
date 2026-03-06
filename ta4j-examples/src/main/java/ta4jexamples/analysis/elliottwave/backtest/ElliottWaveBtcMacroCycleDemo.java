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
import java.util.TreeMap;

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
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.PatternSet;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.Num;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * BTC-first Elliott Wave macro-cycle study and demo.
 *
 * <p>
 * This demo treats BTC macro anchors as the truth set and searches explicit
 * anchor-to-anchor decompositions instead of relying on whichever terminal
 * phase happened to rank highest in the generic runner. Each bullish
 * {@code BOTTOM -> TOP} leg is fitted as a five-wave structure and each bearish
 * {@code TOP -> BOTTOM} leg is fitted as an {@code A-B-C} correction. Multiple
 * logic profiles are then scored side-by-side so the demo can answer two
 * questions reproducibly:
 * <ul>
 * <li>Which constraint profile fits the historical BTC cycles best?</li>
 * <li>Given that winning profile, what phase does the current cycle most
 * resemble?</li>
 * </ul>
 *
 * <p>
 * The demo emits a machine-readable JSON summary and a saved 4K chart. Labels
 * are attached to the selected decomposition pivots, not inferred from
 * scenario-family names, which keeps the rendered wave counts aligned with the
 * chosen BTC fit.
 *
 * @since 0.22.4
 */
public final class ElliottWaveBtcMacroCycleDemo {

    static final String RESULT_PREFIX = "EW_BTC_MACRO_DEMO: ";
    static final Path DEFAULT_CHART_DIRECTORY = Path.of("temp", "charts");
    static final String DEFAULT_CHART_FILE_NAME = "elliott-wave-btc-macro-cycles";
    static final String DEFAULT_SUMMARY_FILE_NAME = "elliott-wave-btc-macro-cycles-summary.json";
    static final int DEFAULT_CHART_WIDTH = 3840;
    static final int DEFAULT_CHART_HEIGHT = 2160;
    static final String RECENT_LOW_ANCHOR_ID = "btc-2022-cycle-bottom";
    static final double DEFAULT_ACCEPTED_SEGMENT_SCORE = 0.64;
    static final int MAX_PIVOT_CANDIDATES = 12;
    static final int LABEL_CLUSTER_BAR_GAP = 18;
    static final Color BULLISH_LEG_COLOR = new Color(0x66BB6A);
    static final Color BEARISH_LEG_COLOR = new Color(0xEF5350);
    static final Color BULLISH_WAVE_COLOR = new Color(0x81C784);
    static final Color BEARISH_WAVE_COLOR = new Color(0xE57373);
    static final Color BULLISH_CANDIDATE_COLOR = new Color(0xC8E6C9);
    static final Color BEARISH_CANDIDATE_COLOR = new Color(0xFFCDD2);
    static final Color ANCHOR_OVERLAY_COLOR = new Color(0xCFD8DC);

    private static final Logger LOG = LogManager.getLogger(ElliottWaveBtcMacroCycleDemo.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double EPSILON = 1e-9;

    private ElliottWaveBtcMacroCycleDemo() {
    }

    /**
     * Runs the BTC macro-cycle study and logs the resulting JSON summary.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        DemoReport report = generateReport(DEFAULT_CHART_DIRECTORY);
        LOG.info("{}{}", RESULT_PREFIX, report.toJson());
    }

    static DemoReport generateReport(Path chartDirectory) {
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        BarSeries series = requireSeries(ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE,
                ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME);
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        MacroStudy study = evaluateMacroStudy(series, registry);
        Optional<Path> chartPath = saveMacroCycleChart(series, registry, study, chartDirectory);
        String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        Path summaryPath = chartDirectory.resolve(DEFAULT_SUMMARY_FILE_NAME).toAbsolutePath().normalize();
        String baselineProfileId = ElliottWaveWalkForwardProfiles.baseline()
                .metadata()
                .getOrDefault("profile", "baseline-minute-f2-h2l2-max25-sw0");
        CurrentCycleSummary currentCycle = study.currentCycle().withChartPath(chartPathText);
        DemoReport report = new DemoReport(registry.version(), registry.datasetResource(), baselineProfileId,
                study.selectedProfile().profile().id(), study.selectedProfile().profile().hypothesisId(),
                study.selectedProfile().historicalFitPassed(),
                "BTC macro-cycle decomposition selected from explicit anchor-to-anchor wave fits", chartPathText,
                summaryPath.toString(), study.profileScores(), study.cycles(), study.hypotheses(), currentCycle);
        saveSummary(report, summaryPath);
        return report;
    }

    static Optional<Path> saveMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, Path chartDirectory) {
        return saveMacroCycleChart(series, registry, evaluateMacroStudy(series, registry), chartDirectory);
    }

    static Optional<Path> saveMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, ElliottWaveAnalysisRunner runner,
            Path chartDirectory) {
        return saveMacroCycleChart(series, registry, evaluateMacroStudy(series, registry), chartDirectory);
    }

    private static Optional<Path> saveMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, MacroStudy study, Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(study, "study");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        JFreeChart chart = renderMacroCycleChart(series, registry, study);
        return chartWorkflow.saveChartImage(chart, series, DEFAULT_CHART_FILE_NAME, DEFAULT_CHART_WIDTH,
                DEFAULT_CHART_HEIGHT);
    }

    static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return renderMacroCycleChart(series, registry, evaluateMacroStudy(series, registry));
    }

    static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, ElliottWaveAnalysisRunner runner) {
        return renderMacroCycleChart(series, registry, evaluateMacroStudy(series, registry));
    }

    private static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, MacroStudy study) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(study, "study");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        List<LegSegment> legSegments = buildLegSegments(registry);
        List<SegmentScenarioFit> segmentFits = study.selectedProfile().chartSegments();
        CurrentPhaseFit currentPrimaryFit = study.currentPrimaryFit();
        int currentCycleStartIndex = currentPrimaryFit == null ? Integer.MAX_VALUE
                : currentPrimaryFit.scenario().swings().getFirst().fromIndex();
        BarSeriesLabelIndicator anchorLabels = new BarSeriesLabelIndicator(series, buildAnchorLabels(series, registry));
        BarSeriesLabelIndicator waveLabels = new BarSeriesLabelIndicator(series,
                buildSegmentWaveLabels(series, segmentFits, currentPrimaryFit));
        FixedIndicator<Num> bullishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, true, true,
                "Bullish accepted wave segments");
        FixedIndicator<Num> bearishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, false, true,
                "Bearish accepted wave segments");
        FixedIndicator<Num> bullishFallbackFits = buildScenarioFitIndicator(series, segmentFits, true, false,
                "Bullish fallback wave segments");
        FixedIndicator<Num> bearishFallbackFits = buildScenarioFitIndicator(series, segmentFits, false, false,
                "Bearish fallback wave segments");
        FixedIndicator<Num> currentCyclePrimaryFit = buildScenarioIndicator(series,
                currentPrimaryFit == null ? null : currentPrimaryFit.scenario(), "Current-cycle wave-count segments");
        FixedIndicator<Num> bullishLegs = buildCycleLegIndicator(series, legSegments, true, "Bullish 1-2-3-4-5",
                currentCycleStartIndex);
        FixedIndicator<Num> bearishLegs = buildCycleLegIndicator(series, legSegments, false, "Bearish A-B-C",
                currentCycleStartIndex);
        ChartPlan plan = chartWorkflow.builder()
                .withTitle("BTC macro cycles: bullish 1-5 tops and bearish A-C lows")
                .withSeries(series)
                .withIndicatorOverlay(bullishLegs)
                .withLineColor(BULLISH_LEG_COLOR)
                .withLineWidth(4.2f)
                .withOpacity(0.60f)
                .withLabel("Bullish 1-2-3-4-5")
                .withIndicatorOverlay(bearishLegs)
                .withLineColor(BEARISH_LEG_COLOR)
                .withLineWidth(4.2f)
                .withOpacity(0.60f)
                .withLabel("Bearish A-B-C")
                .withIndicatorOverlay(bullishAcceptedFits)
                .withLineColor(BULLISH_WAVE_COLOR)
                .withLineWidth(2.4f)
                .withOpacity(0.72f)
                .withLabel("Bullish accepted wave segments")
                .withIndicatorOverlay(bearishAcceptedFits)
                .withLineColor(BEARISH_WAVE_COLOR)
                .withLineWidth(2.4f)
                .withOpacity(0.72f)
                .withLabel("Bearish accepted wave segments")
                .withIndicatorOverlay(bullishFallbackFits)
                .withLineColor(BULLISH_CANDIDATE_COLOR)
                .withLineWidth(1.8f)
                .withOpacity(0.56f)
                .withLabel("Bullish fallback wave segments")
                .withIndicatorOverlay(bearishFallbackFits)
                .withLineColor(BEARISH_CANDIDATE_COLOR)
                .withLineWidth(1.8f)
                .withOpacity(0.56f)
                .withLabel("Bearish fallback wave segments")
                .withIndicatorOverlay(currentCyclePrimaryFit)
                .withLineColor(BULLISH_WAVE_COLOR)
                .withLineWidth(2.8f)
                .withOpacity(0.76f)
                .withLabel("Current-cycle wave-count segments")
                .withIndicatorOverlay(anchorLabels)
                .withLineColor(ANCHOR_OVERLAY_COLOR)
                .withLineWidth(1.5f)
                .withOpacity(0.55f)
                .withLabel("BTC macro-cycle anchors")
                .withIndicatorOverlay(waveLabels)
                .withLineColor(Color.WHITE)
                .withLineWidth(2.5f)
                .withOpacity(0.95f)
                .withLabel("Matched Elliott wave labels")
                .toPlan();
        JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    static MacroStudy evaluateMacroStudy(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");

        List<MacroLogicProfile> profiles = logicProfiles();
        List<LegSegment> chartLegs = buildLegSegments(registry);
        List<MacroCycle> historicalCycles = buildHistoricalCycles(registry);

        List<MacroProfileEvaluation> evaluations = new ArrayList<>();
        for (MacroLogicProfile profile : profiles) {
            evaluations.add(evaluateProfile(series, profile, chartLegs, historicalCycles));
        }
        evaluations.sort(profileEvaluationComparator());
        MacroProfileEvaluation selectedProfile = evaluations.getFirst();
        List<ProfileScoreSummary> profileScores = evaluations.stream().map(ProfileScoreSummary::from).toList();
        List<DirectionalCycleSummary> cycles = selectedProfile.cycleFits()
                .stream()
                .map(DirectionalCycleSummary::from)
                .toList();
        List<HypothesisResult> hypotheses = buildHypotheses(evaluations);
        CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, registry, selectedProfile);
        return new MacroStudy(selectedProfile, List.copyOf(evaluations), profileScores, cycles, hypotheses,
                currentCycle.summary(), currentCycle.primaryFit(), currentCycle.alternateFit());
    }

    static List<BarLabel> buildWaveLabelsFromScenario(BarSeries series, ElliottScenario scenario, Color labelColor) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(labelColor, "labelColor");

        List<ElliottSwing> swings = scenario.swings();
        if (swings.isEmpty()) {
            return List.of();
        }

        List<BarLabel> labels = new ArrayList<>();
        for (int index = 0; index < swings.size(); index++) {
            ElliottSwing swing = swings.get(index);
            boolean highPivot = swing.isRising();
            labels.add(new BarLabel(swing.toIndex(), offsetLabelValue(series, swing.toPrice(), highPivot),
                    waveLabelForPhase(scenario.currentPhase(), index), placementForPivot(highPivot), labelColor));
        }
        return List.copyOf(labels);
    }

    private static MacroProfileEvaluation evaluateProfile(BarSeries series, MacroLogicProfile profile,
            List<LegSegment> chartLegs, List<MacroCycle> historicalCycles) {
        ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        List<SegmentScenarioFit> chartSegments = new ArrayList<>();
        Map<String, SegmentScenarioFit> segmentById = new LinkedHashMap<>();
        for (LegSegment legSegment : chartLegs) {
            fitSegment(series, legSegment, profile, profileRunner).ifPresent(fit -> {
                chartSegments.add(fit);
                segmentById.put(segmentKey(legSegment), fit);
            });
        }

        List<CycleFit> cycleFits = new ArrayList<>();
        for (MacroCycle cycle : historicalCycles) {
            SegmentScenarioFit bullishFit = segmentById.get(segmentKey(cycle.bullishLeg()));
            SegmentScenarioFit bearishFit = segmentById.get(segmentKey(cycle.bearishLeg()));
            cycleFits.add(CycleFit.create(cycle, bullishFit, bearishFit));
        }

        int acceptedCycles = 0;
        int acceptedSegments = 0;
        double scoreTotal = 0.0;
        for (CycleFit cycleFit : cycleFits) {
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
        double aggregateScore = cycleFits.isEmpty() ? 0.0 : scoreTotal / cycleFits.size();
        boolean historicalFitPassed = !cycleFits.isEmpty() && acceptedCycles == cycleFits.size();
        return new MacroProfileEvaluation(profile, aggregateScore, acceptedCycles, acceptedSegments,
                historicalFitPassed, List.copyOf(cycleFits), List.copyOf(chartSegments));
    }

    private static ElliottWaveAnalysisRunner buildProfileRunner(MacroLogicProfile profile) {
        return ElliottWaveAnchorCalibrationHarness.buildMacroAnalysisRunner(profile.runnerDegree(),
                profile.runnerHigherDegrees(), profile.runnerLowerDegrees(), profile.runnerMaxScenarios(),
                profile.runnerScenarioSwingWindow(), profile.runnerFractalWindow(), profile.patternSet(),
                profile.runnerBaseConfidenceWeight(),
                profile.patternAwareConfidence() ? ConfidenceProfiles::patternAwareModel
                        : ConfidenceProfiles::defaultModel);
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

    private static List<HypothesisResult> buildHypotheses(List<MacroProfileEvaluation> evaluations) {
        MacroProfileEvaluation classical = findEvaluation(evaluations, "orthodox-classical");
        MacroProfileEvaluation hierarchical = findEvaluation(evaluations, "h1-hierarchical-swing");
        MacroProfileEvaluation relaxedImpulse = findEvaluation(evaluations, "h2-btc-relaxed-impulse");
        MacroProfileEvaluation relaxedCorrective = findEvaluation(evaluations, "h3-btc-relaxed-corrective");
        MacroProfileEvaluation anchorFirst = findEvaluation(evaluations, "h4-anchor-first-hybrid");

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

    private static HypothesisResult hypothesisFromProfile(String id, String title, MacroProfileEvaluation candidate,
            MacroProfileEvaluation baseline, String supportedSummary, String rejectedSummary) {
        boolean supported = candidate.aggregateScore() > baseline.aggregateScore() + 0.01
                || candidate.acceptedCycles() > baseline.acceptedCycles();
        Map<String, String> evidence = orderedEvidence("candidateProfile", candidate.profile().id(), "candidateScore",
                formatScore(candidate.aggregateScore()), "candidateAcceptedCycles",
                Integer.toString(candidate.acceptedCycles()), "baselineProfile", baseline.profile().id(),
                "baselineScore", formatScore(baseline.aggregateScore()), "baselineAcceptedCycles",
                Integer.toString(baseline.acceptedCycles()));
        return new HypothesisResult(id, title, supported, supported ? supportedSummary : rejectedSummary, evidence);
    }

    private static MacroProfileEvaluation findEvaluation(List<MacroProfileEvaluation> evaluations, String profileId) {
        return evaluations.stream()
                .filter(evaluation -> evaluation.profile().id().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing profile " + profileId));
    }

    private static CurrentCycleAnalysis evaluateCurrentCycle(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, MacroProfileEvaluation selectedProfile) {
        ElliottWaveAnchorCalibrationHarness.Anchor lowAnchor = findAnchorOrLatestBottom(registry, RECENT_LOW_ANCHOR_ID);
        int startIndex = nearestIndex(series, lowAnchor.at());
        int endIndex = series.getEndIndex();
        CurrentPhaseFit primary = null;
        CurrentPhaseFit alternate = null;
        for (int phase = 1; phase <= 5; phase++) {
            Optional<CurrentPhaseFit> fit = fitCurrentBullishPhase(series, startIndex, endIndex,
                    selectedProfile.profile(), phase);
            if (fit.isEmpty()) {
                continue;
            }
            CurrentPhaseFit candidate = fit.get();
            if (primary == null || candidate.compareTo(primary) < 0) {
                if (primary != null && primary.currentPhase() != candidate.currentPhase()) {
                    alternate = primary;
                }
                primary = candidate;
                continue;
            }
            if ((alternate == null || candidate.compareTo(alternate) < 0)
                    && (primary == null || candidate.currentPhase() != primary.currentPhase())) {
                alternate = candidate;
            }
        }

        String historicalStatus = selectedProfile.historicalFitPassed() ? "historical BTC fit passed"
                : "historical BTC fit still partial";
        String winningProfile = selectedProfile.profile().id();
        String primaryCount = primary == null ? "No current bullish count" : primary.countLabel();
        String alternateCount = alternate == null ? "" : alternate.countLabel();
        String currentWave = primary == null ? "" : primary.currentPhase().name();
        String invalidation = primary == null ? "" : formatNum(primary.invalidationPrice());
        String startTimeUtc = lowAnchor.at().toString();
        String latestTimeUtc = series.getLastBar().getEndTime().toString();
        CurrentCycleSummary summary = new CurrentCycleSummary(startTimeUtc, latestTimeUtc, winningProfile,
                historicalStatus, primaryCount, alternateCount, currentWave, invalidation,
                primary == null ? 0.0 : primary.fitScore(), alternate == null ? 0.0 : alternate.fitScore(), "");
        return new CurrentCycleAnalysis(summary, primary, alternate);
    }

    private static Optional<CurrentPhaseFit> fitCurrentBullishPhase(BarSeries series, int startIndex, int endIndex,
            MacroLogicProfile profile, int phase) {
        if (phase < 1 || phase > 5 || endIndex <= startIndex) {
            return Optional.empty();
        }
        if (phase == 1) {
            Num startPrice = pivotPrice(series, startIndex, false);
            Num endPrice = pivotPrice(series, endIndex, true);
            if (endPrice.doubleValue() <= startPrice.doubleValue()) {
                return Optional.empty();
            }
            ElliottScenario scenario = buildPartialImpulseScenario(series, startIndex, List.of(), endIndex,
                    ElliottPhase.WAVE1);
            double fitScore = clamp((endPrice.doubleValue() - startPrice.doubleValue())
                    / Math.max(EPSILON, segmentRange(series, startIndex, endIndex)), 0.0, 1.0);
            return Optional.of(
                    new CurrentPhaseFit(scenario, ElliottPhase.WAVE1, fitScore, startPrice, "Bullish 1", startPrice));
        }

        boolean[] internalHighPivots = switch (phase) {
        case 2 -> new boolean[] { true };
        case 3 -> new boolean[] { true, false };
        case 4 -> new boolean[] { true, false, true };
        case 5 -> new boolean[] { true, false, true, false };
        default -> throw new IllegalArgumentException("Unsupported phase " + phase);
        };
        double[] fractions = Arrays.copyOf(logicProfiles().getFirst().impulseFractions(), internalHighPivots.length);
        List<List<PivotCandidate>> candidatesBySlot = collectSlotCandidates(series, startIndex, endIndex, fractions,
                internalHighPivots, profile);
        List<PivotCandidate> bestPath = searchBestPath(candidatesBySlot, startIndex, endIndex,
                minimumSwingGap(startIndex, endIndex, phase));
        if (bestPath.isEmpty()) {
            return Optional.empty();
        }

        ElliottPhase currentPhase = switch (phase) {
        case 2 -> ElliottPhase.WAVE2;
        case 3 -> ElliottPhase.WAVE3;
        case 4 -> ElliottPhase.WAVE4;
        case 5 -> ElliottPhase.WAVE5;
        default -> ElliottPhase.WAVE1;
        };
        ElliottScenario scenario = buildPartialImpulseScenario(series, startIndex, bestPath, endIndex, currentPhase);
        double fitScore = scorePartialImpulse(series, startIndex, bestPath, endIndex, profile, phase);
        Num invalidationPrice = switch (phase) {
        case 2 -> pivotPrice(series, startIndex, false);
        case 3 -> bestPath.get(1).price();
        case 4 -> bestPath.get(0).price();
        case 5 -> bestPath.getLast().price();
        default -> pivotPrice(series, startIndex, false);
        };
        return Optional.of(new CurrentPhaseFit(scenario, currentPhase, fitScore, pivotPrice(series, startIndex, false),
                bullishCountLabel(phase), invalidationPrice));
    }

    private static double scorePartialImpulse(BarSeries series, int startIndex, List<PivotCandidate> path, int endIndex,
            MacroLogicProfile profile, int phase) {
        List<Double> prices = new ArrayList<>();
        prices.add(pivotPrice(series, startIndex, false).doubleValue());
        for (PivotCandidate candidate : path) {
            prices.add(candidate.price().doubleValue());
        }
        prices.add(pivotPrice(series, endIndex, phase % 2 == 1).doubleValue());
        List<Integer> indices = new ArrayList<>();
        indices.add(startIndex);
        for (PivotCandidate candidate : path) {
            indices.add(candidate.barIndex());
        }
        indices.add(endIndex);

        double spacing = durationBalanceScore(indices);
        double strength = average(path.stream().mapToDouble(PivotCandidate::normalizedScore).toArray(), 0.50);

        double structure;
        double rules;
        if (phase == 2) {
            double wave1 = prices.get(1) - prices.get(0);
            double wave2Retrace = (prices.get(1) - prices.get(2)) / Math.max(EPSILON, wave1);
            structure = boundedScore(wave2Retrace, 0.20, 0.95 + (profile.impulseRelaxation() * 0.35), 0.75);
            rules = prices.get(2) > prices.get(0) ? 1.0 : 0.35 + (profile.impulseRelaxation() * 0.45);
        } else if (phase == 3) {
            double wave1 = prices.get(1) - prices.get(0);
            double wave3 = prices.get(3) - prices.get(2);
            structure = boundedScore(wave3 / Math.max(EPSILON, wave1), 0.80, 4.00, 2.25);
            rules = prices.get(2) > prices.get(0) ? 1.0 : 0.35 + (profile.impulseRelaxation() * 0.45);
        } else if (phase == 4) {
            double wave3 = prices.get(3) - prices.get(2);
            double wave4Retrace = (prices.get(3) - prices.get(4)) / Math.max(EPSILON, wave3);
            structure = boundedScore(wave4Retrace, 0.10, 0.55 + (profile.impulseRelaxation() * 0.25), 0.70);
            rules = prices.get(4) > prices.get(1) ? 1.0 : 0.30 + (profile.impulseRelaxation() * 0.60);
        } else {
            double wave1 = prices.get(1) - prices.get(0);
            double wave3 = prices.get(3) - prices.get(2);
            double wave5 = prices.get(5) - prices.get(4);
            structure = average(new double[] { boundedScore(wave3 / Math.max(EPSILON, wave1), 0.90, 4.50, 2.25),
                    boundedScore(wave5 / Math.max(EPSILON, wave1), 0.20, 3.50, 2.25) }, 0.0);
            rules = average(
                    new double[] { prices.get(2) > prices.get(0) ? 1.0 : 0.35 + (profile.impulseRelaxation() * 0.45),
                            prices.get(4) > prices.get(1) ? 1.0 : 0.30 + (profile.impulseRelaxation() * 0.60),
                            wave3 >= Math.min(wave1, wave5) ? 1.0 : 0.30 + (profile.impulseRelaxation() * 0.50) },
                    0.0);
        }
        return weightedScore(structure, rules, spacing, strength, profile);
    }

    private static Optional<SegmentScenarioFit> fitSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner) {
        int startIndex = nearestIndex(series, legSegment.fromAnchor().at());
        int endIndex = nearestIndex(series, legSegment.toAnchor().at());
        if (endIndex <= startIndex) {
            return Optional.empty();
        }
        if (legSegment.bullish()) {
            return fitBullishSegment(series, legSegment, profile, profileRunner, startIndex, endIndex);
        }
        return fitBearishSegment(series, legSegment, profile, profileRunner, startIndex, endIndex);
    }

    private static Optional<SegmentScenarioFit> fitBullishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner, int startIndex, int endIndex) {
        if (endIndex - startIndex < 5) {
            return Optional.empty();
        }
        Optional<SegmentScenarioFit> coreFit = fitBullishSegmentFromCoreRunner(series, legSegment, profile,
                profileRunner, startIndex, endIndex);
        boolean[] internalHighPivots = { true, false, true, false };
        List<List<PivotCandidate>> candidatesBySlot = collectSlotCandidates(series, startIndex, endIndex,
                profile.impulseFractions(), internalHighPivots, profile);
        SegmentScenarioFit bestFit = searchBullishSegment(series, legSegment, profile, startIndex, endIndex,
                candidatesBySlot);
        if (bestFit != null) {
            return Optional.of(preferCoreFit(coreFit.orElse(null), bestFit));
        }
        if (coreFit.isPresent()) {
            return coreFit;
        }
        return buildFallbackSegment(series, legSegment, profile, startIndex, endIndex, internalHighPivots,
                profile.impulseFractions(), ElliottPhase.WAVE5, ScenarioType.IMPULSE, true);
    }

    private static Optional<SegmentScenarioFit> fitBearishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner, int startIndex, int endIndex) {
        if (endIndex - startIndex < 3) {
            return Optional.empty();
        }
        Optional<SegmentScenarioFit> coreFit = fitBearishSegmentFromCoreRunner(series, legSegment, profile,
                profileRunner, startIndex, endIndex);
        boolean[] internalHighPivots = { false, true };
        List<List<PivotCandidate>> candidatesBySlot = collectSlotCandidates(series, startIndex, endIndex,
                profile.correctiveFractions(), internalHighPivots, profile);
        SegmentScenarioFit bestFit = searchBearishSegment(series, legSegment, profile, startIndex, endIndex,
                candidatesBySlot);
        if (bestFit != null) {
            return Optional.of(preferCoreFit(coreFit.orElse(null), bestFit));
        }
        if (coreFit.isPresent()) {
            return coreFit;
        }
        return buildFallbackSegment(series, legSegment, profile, startIndex, endIndex, internalHighPivots,
                profile.correctiveFractions(), ElliottPhase.CORRECTIVE_C, ScenarioType.CORRECTIVE_COMPLEX, false);
    }

    private static SegmentScenarioFit preferCoreFit(SegmentScenarioFit coreFit, SegmentScenarioFit localFit) {
        if (coreFit == null) {
            return localFit;
        }
        if (localFit == null) {
            return coreFit;
        }
        if (coreFit.accepted() && (!localFit.accepted() || coreFit.fitScore() + 0.05 >= localFit.fitScore())) {
            return coreFit;
        }
        return coreFit.compareTo(localFit) < 0 ? coreFit : localFit;
    }

    private static Optional<SegmentScenarioFit> fitBullishSegmentFromCoreRunner(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner, int startIndex, int endIndex) {
        return fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, true);
    }

    private static Optional<SegmentScenarioFit> fitBearishSegmentFromCoreRunner(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner, int startIndex, int endIndex) {
        return fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, false);
    }

    private static Optional<SegmentScenarioFit> fitSegmentFromCoreRunner(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner, int startIndex, int endIndex,
            boolean bullish) {
        BarSeries segmentSeries = series.getSubSeries(startIndex, endIndex + 1);
        ElliottWaveAnalysisResult analysis = profileRunner.analyze(segmentSeries);
        int segmentEndIndex = segmentSeries.getEndIndex();
        for (ElliottWaveAnalysisResult.BaseScenarioAssessment assessment : analysis.rankedBaseScenarios()) {
            ElliottScenario candidate = assessment.scenario();
            if (!matchesSegmentScenario(candidate, bullish, segmentEndIndex)) {
                continue;
            }
            ElliottScenario rebased = rebaseScenario(candidate, startIndex);
            SegmentScenarioFit fit = bullish
                    ? scoreBullishSegment(series, legSegment, profile, startIndex, endIndex,
                            pivotCandidatesFromScenario(rebased, 4, assessment.compositeScore()), rebased,
                            "Core-ranked IMPULSE WAVE5 fit")
                    : scoreBearishSegment(series, legSegment, profile, startIndex, endIndex,
                            pivotCandidatesFromScenario(rebased, 2, assessment.compositeScore()), rebased,
                            "Core-ranked corrective C fit");
            if (fit != null) {
                return Optional.of(fit);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesSegmentScenario(ElliottScenario scenario, boolean bullish, int segmentEndIndex) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return false;
        }
        if (scenario.swings().getFirst().fromIndex() != 0 || scenario.swings().getLast().toIndex() != segmentEndIndex) {
            return false;
        }
        if (!scenario.hasKnownDirection()) {
            return false;
        }
        if (bullish) {
            return scenario.type() == ScenarioType.IMPULSE && scenario.currentPhase() == ElliottPhase.WAVE5
                    && scenario.waveCount() == 5 && scenario.isBullish();
        }
        return scenario.type().isCorrective() && scenario.currentPhase() == ElliottPhase.CORRECTIVE_C
                && scenario.waveCount() == 3 && scenario.isBearish();
    }

    private static List<PivotCandidate> pivotCandidatesFromScenario(ElliottScenario scenario, int internalPivotCount,
            double normalizedScore) {
        List<PivotCandidate> candidates = new ArrayList<>(internalPivotCount);
        for (int index = 0; index < internalPivotCount; index++) {
            ElliottSwing swing = scenario.swings().get(index);
            candidates.add(new PivotCandidate(swing.toIndex(), swing.toPrice(), normalizedScore, normalizedScore));
        }
        return List.copyOf(candidates);
    }

    private static ElliottScenario rebaseScenario(ElliottScenario scenario, int indexOffset) {
        List<ElliottSwing> rebasedSwings = scenario.swings()
                .stream()
                .map(swing -> new ElliottSwing(swing.fromIndex() + indexOffset, swing.toIndex() + indexOffset,
                        swing.fromPrice(), swing.toPrice(), swing.degree()))
                .toList();
        ElliottScenario.Builder builder = ElliottScenario.builder()
                .id(scenario.id())
                .currentPhase(scenario.currentPhase())
                .swings(rebasedSwings)
                .confidence(scenario.confidence())
                .degree(scenario.degree())
                .invalidationPrice(scenario.invalidationPrice())
                .primaryTarget(scenario.primaryTarget())
                .fibonacciTargets(scenario.fibonacciTargets())
                .type(scenario.type())
                .startIndex(scenario.startIndex() + indexOffset);
        if (scenario.hasKnownDirection()) {
            builder.bullishDirection(scenario.isBullish());
        }
        return builder.build();
    }

    private static SegmentScenarioFit searchBullishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, List<List<PivotCandidate>> candidatesBySlot) {
        List<PivotCandidate> chosen = new ArrayList<>();
        SegmentScenarioFit[] best = new SegmentScenarioFit[1];
        searchSegmentPaths(candidatesBySlot, 0, startIndex, endIndex, minimumSwingGap(startIndex, endIndex, 5), chosen,
                candidates -> {
                    SegmentScenarioFit fit = scoreBullishSegment(series, legSegment, profile, startIndex, endIndex,
                            candidates);
                    if (fit != null && (best[0] == null || fit.compareTo(best[0]) < 0)) {
                        best[0] = fit;
                    }
                });
        return best[0];
    }

    private static SegmentScenarioFit searchBearishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, List<List<PivotCandidate>> candidatesBySlot) {
        List<PivotCandidate> chosen = new ArrayList<>();
        SegmentScenarioFit[] best = new SegmentScenarioFit[1];
        searchSegmentPaths(candidatesBySlot, 0, startIndex, endIndex, minimumSwingGap(startIndex, endIndex, 3), chosen,
                candidates -> {
                    SegmentScenarioFit fit = scoreBearishSegment(series, legSegment, profile, startIndex, endIndex,
                            candidates);
                    if (fit != null && (best[0] == null || fit.compareTo(best[0]) < 0)) {
                        best[0] = fit;
                    }
                });
        return best[0];
    }

    private static void searchSegmentPaths(List<List<PivotCandidate>> candidatesBySlot, int slotIndex, int startIndex,
            int endIndex, int minimumGap, List<PivotCandidate> chosen,
            java.util.function.Consumer<List<PivotCandidate>> consumer) {
        if (slotIndex == candidatesBySlot.size()) {
            consumer.accept(List.copyOf(chosen));
            return;
        }
        int previousIndex = chosen.isEmpty() ? startIndex : chosen.getLast().barIndex();
        int remainingSlots = candidatesBySlot.size() - slotIndex - 1;
        int latestAllowed = endIndex - ((remainingSlots + 1) * minimumGap);
        for (PivotCandidate candidate : candidatesBySlot.get(slotIndex)) {
            if (candidate.barIndex() <= previousIndex + minimumGap) {
                continue;
            }
            if (candidate.barIndex() > latestAllowed) {
                continue;
            }
            chosen.add(candidate);
            searchSegmentPaths(candidatesBySlot, slotIndex + 1, startIndex, endIndex, minimumGap, chosen, consumer);
            chosen.removeLast();
        }
    }

    private static List<PivotCandidate> searchBestPath(List<List<PivotCandidate>> candidatesBySlot, int startIndex,
            int endIndex, int minimumGap) {
        final class BestPathHolder {
            private List<PivotCandidate> best = List.of();
            private double score = Double.NEGATIVE_INFINITY;
        }
        BestPathHolder holder = new BestPathHolder();
        searchSegmentPaths(candidatesBySlot, 0, startIndex, endIndex, minimumGap, new ArrayList<>(), candidates -> {
            double score = candidates.stream().mapToDouble(PivotCandidate::normalizedScore).sum();
            if (score > holder.score) {
                holder.score = score;
                holder.best = List.copyOf(candidates);
            }
        });
        return holder.best;
    }

    private static SegmentScenarioFit scoreBullishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, List<PivotCandidate> candidates) {
        return scoreBullishSegment(series, legSegment, profile, startIndex, endIndex, candidates, null,
                "Bullish 1-2-3-4-5 decomposition");
    }

    private static SegmentScenarioFit scoreBullishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, List<PivotCandidate> candidates,
            ElliottScenario scenarioOverride, String rationale) {
        double p0 = pivotPrice(series, startIndex, false).doubleValue();
        double p1 = candidates.get(0).price().doubleValue();
        double p2 = candidates.get(1).price().doubleValue();
        double p3 = candidates.get(2).price().doubleValue();
        double p4 = candidates.get(3).price().doubleValue();
        double p5 = pivotPrice(series, endIndex, true).doubleValue();
        if (!(p1 > p0 && p2 < p1 && p3 > p2 && p4 < p3 && p5 > p4)) {
            return null;
        }

        double segmentRange = Math.max(EPSILON, segmentRange(series, startIndex, endIndex));
        double wave1 = p1 - p0;
        double wave2 = p1 - p2;
        double wave3 = p3 - p2;
        double wave4 = p3 - p4;
        double wave5 = p5 - p4;
        double wave2Retrace = wave2 / Math.max(EPSILON, wave1);
        double wave4Retrace = wave4 / Math.max(EPSILON, wave3);
        double wave3Ratio = wave3 / Math.max(EPSILON, wave1);
        double wave5Ratio = wave5 / Math.max(EPSILON, wave1);

        double structureScore = average(
                new double[] { boundedScore(wave2Retrace, 0.20, 0.95 + (profile.impulseRelaxation() * 0.35), 0.75),
                        boundedScore(wave4Retrace, 0.10, 0.55 + (profile.impulseRelaxation() * 0.25), 0.70),
                        boundedScore(wave3Ratio, 0.90 - (profile.impulseRelaxation() * 0.15), 4.50, 2.25),
                        boundedScore(wave5Ratio, 0.20, 3.50, 2.25) },
                0.0);

        double wave2FloorScore = p2 > p0 ? 1.0
                : clamp(1.0 - (((p0 - p2) / segmentRange) / (0.02 + (profile.impulseRelaxation() * 0.18))), 0.0, 1.0);
        double wave4OverlapScore = p4 > p1 ? 1.0
                : clamp(1.0 - (((p1 - p4) / segmentRange) / (0.03 + (profile.impulseRelaxation() * 0.30))), 0.0, 1.0);
        double wave3ShortestScore = wave3 >= Math.min(wave1, wave5) ? 1.0 : 0.30 + (profile.impulseRelaxation() * 0.50);
        double terminalScore = p5 > p3 ? 1.0 : 0.25 + (profile.impulseRelaxation() * 0.45);
        double ruleScore = average(
                new double[] { wave2FloorScore, wave4OverlapScore, wave3ShortestScore, terminalScore }, 0.0);
        double spacingScore = spacingScore(startIndex, endIndex, candidates, profile.impulseFractions());
        double strengthScore = average(candidates.stream().mapToDouble(PivotCandidate::normalizedScore).toArray(),
                0.20);
        double fitScore = weightedScore(structureScore, ruleScore, spacingScore, strengthScore, profile);
        ElliottScenario scenario = scenarioOverride == null
                ? buildImpulseScenario(series, startIndex, candidates, endIndex)
                : scenarioOverride;
        boolean accepted = fitScore >= Math.max(DEFAULT_ACCEPTED_SEGMENT_SCORE, profile.acceptanceThreshold())
                && ruleScore >= 0.45 && spacingScore >= 0.35;
        return new SegmentScenarioFit(legSegment, scenario, fitScore, structureScore, ruleScore, spacingScore,
                strengthScore, true, accepted, rationale);
    }

    private static SegmentScenarioFit scoreBearishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, List<PivotCandidate> candidates) {
        return scoreBearishSegment(series, legSegment, profile, startIndex, endIndex, candidates, null,
                "Bearish A-B-C decomposition");
    }

    private static SegmentScenarioFit scoreBearishSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, List<PivotCandidate> candidates,
            ElliottScenario scenarioOverride, String rationale) {
        double p0 = pivotPrice(series, startIndex, true).doubleValue();
        double p1 = candidates.get(0).price().doubleValue();
        double p2 = candidates.get(1).price().doubleValue();
        double p3 = pivotPrice(series, endIndex, false).doubleValue();
        if (!(p1 < p0 && p2 > p1 && p3 < p2)) {
            return null;
        }

        double segmentRange = Math.max(EPSILON, segmentRange(series, startIndex, endIndex));
        double waveA = p0 - p1;
        double waveB = p2 - p1;
        double waveC = p2 - p3;
        double bRatio = waveB / Math.max(EPSILON, waveA);
        double cRatio = waveC / Math.max(EPSILON, waveA);
        double structureScore = average(new double[] {
                boundedScore(bRatio, 0.35, 1.00 + (profile.correctiveRelaxation() * 0.50), 0.60),
                boundedScore(cRatio, Math.max(0.30, 0.75 - (profile.correctiveRelaxation() * 0.25)), 2.40, 1.50) },
                0.0);
        double expandedBScore = p2 <= p0 ? 1.0
                : clamp(1.0 - (((p2 - p0) / segmentRange) / (0.04 + (profile.correctiveRelaxation() * 0.40))), 0.0,
                        1.0);
        double truncatedCScore = p3 < p1 ? 1.0
                : clamp(1.0 - (((p3 - p1) / segmentRange) / (0.03 + (profile.correctiveRelaxation() * 0.30))), 0.0,
                        1.0);
        double ruleScore = average(new double[] { expandedBScore, truncatedCScore }, 0.0);
        double spacingScore = spacingScore(startIndex, endIndex, candidates, profile.correctiveFractions());
        double strengthScore = average(candidates.stream().mapToDouble(PivotCandidate::normalizedScore).toArray(),
                0.20);
        double fitScore = weightedScore(structureScore, ruleScore, spacingScore, strengthScore, profile);
        ElliottScenario scenario = scenarioOverride == null
                ? buildCorrectiveScenario(series, startIndex, candidates, endIndex, profile)
                : scenarioOverride;
        boolean accepted = fitScore >= Math.max(DEFAULT_ACCEPTED_SEGMENT_SCORE, profile.acceptanceThreshold())
                && ruleScore >= 0.35 && spacingScore >= 0.35;
        return new SegmentScenarioFit(legSegment, scenario, fitScore, structureScore, ruleScore, spacingScore,
                strengthScore, false, accepted, rationale);
    }

    private static Optional<SegmentScenarioFit> buildFallbackSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, int startIndex, int endIndex, boolean[] internalHighPivots, double[] fractions,
            ElliottPhase finalPhase, ScenarioType defaultType, boolean bullish) {
        List<PivotCandidate> fallbackCandidates = buildFallbackCandidates(series, startIndex, endIndex, fractions,
                internalHighPivots);
        if (fallbackCandidates.size() != internalHighPivots.length) {
            return Optional.empty();
        }
        ElliottScenario scenario = finalPhase.isImpulse()
                ? buildImpulseScenario(series, startIndex, fallbackCandidates, endIndex)
                : buildCorrectiveScenario(series, startIndex, fallbackCandidates, endIndex, profile);
        SegmentScenarioFit fit;
        if (bullish) {
            fit = scoreBullishSegment(series, legSegment, profile, startIndex, endIndex, fallbackCandidates);
        } else {
            fit = scoreBearishSegment(series, legSegment, profile, startIndex, endIndex, fallbackCandidates);
        }
        if (fit != null) {
            return Optional.of(new SegmentScenarioFit(legSegment, fit.scenario(), fit.fitScore(), fit.structureScore(),
                    fit.ruleScore(), fit.spacingScore(), fit.strengthScore(), bullish, false,
                    "Fallback " + scenario.type().name() + " decomposition"));
        }
        ElliottScenario fallbackScenario = ElliottScenario.builder()
                .id((bullish ? "btc-fallback-impulse-" : "btc-fallback-corrective-") + startIndex + "-" + endIndex)
                .currentPhase(finalPhase)
                .swings(scenario.swings())
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.PRIMARY)
                .type(defaultType)
                .startIndex(startIndex)
                .invalidationPrice(
                        bullish ? pivotPrice(series, startIndex, false) : pivotPrice(series, startIndex, true))
                .build();
        return Optional.of(new SegmentScenarioFit(legSegment, fallbackScenario, 0.20, 0.20, 0.20, 0.20, 0.20, bullish,
                false, "Fallback seeded decomposition"));
    }

    private static List<PivotCandidate> buildFallbackCandidates(BarSeries series, int startIndex, int endIndex,
            double[] fractions, boolean[] highPivots) {
        List<PivotCandidate> candidates = new ArrayList<>();
        int segmentLength = endIndex - startIndex;
        int previousIndex = startIndex;
        for (int index = 0; index < fractions.length; index++) {
            int targetIndex = startIndex + (int) Math.round(segmentLength * fractions[index]);
            int remaining = fractions.length - index;
            int clampedIndex = clampIndex(targetIndex, previousIndex + 1, endIndex - remaining);
            PivotCandidate seeded = seedCandidate(series, startIndex, endIndex, highPivots[index], clampedIndex,
                    fractions[index], 0.10);
            if (seeded == null) {
                return List.of();
            }
            candidates.add(seeded);
            previousIndex = seeded.barIndex();
        }
        return List.copyOf(candidates);
    }

    private static List<List<PivotCandidate>> collectSlotCandidates(BarSeries series, int startIndex, int endIndex,
            double[] expectedFractions, boolean[] internalHighPivots, MacroLogicProfile profile) {
        List<List<PivotCandidate>> candidatesBySlot = new ArrayList<>();
        for (int slot = 0; slot < internalHighPivots.length; slot++) {
            candidatesBySlot.add(collectPivotCandidates(series, startIndex, endIndex, internalHighPivots[slot],
                    expectedFractions[slot], profile));
        }
        return List.copyOf(candidatesBySlot);
    }

    private static List<PivotCandidate> collectPivotCandidates(BarSeries series, int startIndex, int endIndex,
            boolean highPivot, double expectedFraction, MacroLogicProfile profile) {
        int segmentLength = endIndex - startIndex;
        double segmentRange = Math.max(EPSILON, segmentRange(series, startIndex, endIndex));
        Map<Integer, CandidateAccumulator> accumulators = new LinkedHashMap<>();
        for (int radius : profile.pivotRadii()) {
            for (int index = startIndex + 1; index < endIndex; index++) {
                if (!isLocalPivot(series, index, startIndex, endIndex, radius, highPivot)) {
                    continue;
                }
                double prominence = pivotProminence(series, index, startIndex, endIndex, radius, highPivot)
                        / segmentRange;
                double timeScore = 1.0
                        - Math.min(1.0, Math.abs(((index - startIndex) / (double) segmentLength) - expectedFraction));
                double rawScore = 1.0 + (radius / 50.0) + prominence + (profile.expectedFractionWeight() * timeScore);
                final int candidateIndex = index;
                CandidateAccumulator accumulator = accumulators.computeIfAbsent(index,
                        ignored -> new CandidateAccumulator(candidateIndex,
                                pivotPrice(series, candidateIndex, highPivot)));
                accumulator.add(rawScore);
            }
        }

        PivotCandidate seededExpected = seedCandidate(series, startIndex, endIndex, highPivot,
                startIndex + (int) Math.round(segmentLength * expectedFraction), expectedFraction, 0.40);
        if (seededExpected != null) {
            accumulators
                    .computeIfAbsent(seededExpected.barIndex(),
                            ignored -> new CandidateAccumulator(seededExpected.barIndex(), seededExpected.price()))
                    .add(seededExpected.rawScore());
        }

        List<PivotCandidate> candidates = accumulators.values()
                .stream()
                .map(accumulator -> accumulator.toCandidate(startIndex, endIndex))
                .sorted(Comparator.comparingDouble(PivotCandidate::rawScore)
                        .reversed()
                        .thenComparingInt(PivotCandidate::barIndex))
                .limit(MAX_PIVOT_CANDIDATES)
                .sorted(Comparator.comparingInt(PivotCandidate::barIndex))
                .toList();
        return List.copyOf(candidates);
    }

    private static PivotCandidate seedCandidate(BarSeries series, int startIndex, int endIndex, boolean highPivot,
            int targetIndex, double expectedFraction, double baseScore) {
        int segmentLength = endIndex - startIndex;
        int window = Math.max(2, Math.min(30, segmentLength / 12));
        int fromIndex = Math.max(startIndex + 1, targetIndex - window);
        int toIndex = Math.min(endIndex - 1, targetIndex + window);
        if (toIndex < fromIndex) {
            return null;
        }
        int bestIndex = fromIndex;
        double bestPrice = highPivot ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            double price = pivotPrice(series, index, highPivot).doubleValue();
            if ((highPivot && price > bestPrice) || (!highPivot && price < bestPrice)) {
                bestIndex = index;
                bestPrice = price;
            }
        }
        double timeScore = 1.0 - Math.min(1.0,
                Math.abs(((bestIndex - startIndex) / (double) Math.max(1, segmentLength)) - expectedFraction));
        return new PivotCandidate(bestIndex, pivotPrice(series, bestIndex, highPivot), baseScore + timeScore,
                clamp(baseScore + timeScore, 0.0, 1.0));
    }

    private static boolean isLocalPivot(BarSeries series, int pivotIndex, int startIndex, int endIndex, int radius,
            boolean highPivot) {
        int fromIndex = Math.max(startIndex + 1, pivotIndex - radius);
        int toIndex = Math.min(endIndex - 1, pivotIndex + radius);
        double pivotPrice = pivotPrice(series, pivotIndex, highPivot).doubleValue();
        for (int index = fromIndex; index <= toIndex; index++) {
            if (index == pivotIndex) {
                continue;
            }
            double candidate = pivotPrice(series, index, highPivot).doubleValue();
            if ((highPivot && candidate > pivotPrice) || (!highPivot && candidate < pivotPrice)) {
                return false;
            }
        }
        return true;
    }

    private static double pivotProminence(BarSeries series, int pivotIndex, int startIndex, int endIndex, int radius,
            boolean highPivot) {
        int fromIndex = Math.max(startIndex, pivotIndex - radius);
        int toIndex = Math.min(endIndex, pivotIndex + radius);
        double pivotPrice = pivotPrice(series, pivotIndex, highPivot).doubleValue();
        double surroundingExtreme = highPivot ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            if (index == pivotIndex) {
                continue;
            }
            double candidate = highPivot ? series.getBar(index).getLowPrice().doubleValue()
                    : series.getBar(index).getHighPrice().doubleValue();
            if (highPivot) {
                surroundingExtreme = Math.min(surroundingExtreme, candidate);
            } else {
                surroundingExtreme = Math.max(surroundingExtreme, candidate);
            }
        }
        if (highPivot) {
            return pivotPrice - surroundingExtreme;
        }
        return surroundingExtreme - pivotPrice;
    }

    private static ElliottScenario buildImpulseScenario(BarSeries series, int startIndex,
            List<PivotCandidate> candidates, int endIndex) {
        List<ElliottSwing> swings = new ArrayList<>();
        int fromIndex = startIndex;
        Num fromPrice = pivotPrice(series, startIndex, false);
        for (PivotCandidate candidate : candidates) {
            swings.add(new ElliottSwing(fromIndex, candidate.barIndex(), fromPrice, candidate.price(),
                    ElliottDegree.PRIMARY));
            fromIndex = candidate.barIndex();
            fromPrice = candidate.price();
        }
        swings.add(new ElliottSwing(fromIndex, endIndex, fromPrice, pivotPrice(series, endIndex, true),
                ElliottDegree.PRIMARY));
        return ElliottScenario.builder()
                .id("btc-impulse-" + startIndex + "-" + endIndex)
                .currentPhase(ElliottPhase.WAVE5)
                .swings(List.copyOf(swings))
                .confidence(confidenceFor(series, List.copyOf(swings), ElliottPhase.WAVE5))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(candidates.getLast().price())
                .primaryTarget(pivotPrice(series, endIndex, true))
                .type(ScenarioType.IMPULSE)
                .startIndex(startIndex)
                .build();
    }

    private static ElliottScenario buildCorrectiveScenario(BarSeries series, int startIndex,
            List<PivotCandidate> candidates, int endIndex, MacroLogicProfile profile) {
        List<ElliottSwing> swings = new ArrayList<>();
        int fromIndex = startIndex;
        Num fromPrice = pivotPrice(series, startIndex, true);
        for (PivotCandidate candidate : candidates) {
            swings.add(new ElliottSwing(fromIndex, candidate.barIndex(), fromPrice, candidate.price(),
                    ElliottDegree.PRIMARY));
            fromIndex = candidate.barIndex();
            fromPrice = candidate.price();
        }
        Num endPrice = pivotPrice(series, endIndex, false);
        swings.add(new ElliottSwing(fromIndex, endIndex, fromPrice, endPrice, ElliottDegree.PRIMARY));
        ScenarioType scenarioType = classifyCorrectiveType(startIndex, candidates, endIndex, series, profile);
        return ElliottScenario.builder()
                .id("btc-corrective-" + startIndex + "-" + endIndex)
                .currentPhase(ElliottPhase.CORRECTIVE_C)
                .swings(List.copyOf(swings))
                .confidence(confidenceFor(series, List.copyOf(swings), ElliottPhase.CORRECTIVE_C))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(candidates.get(1).price())
                .primaryTarget(endPrice)
                .type(scenarioType)
                .startIndex(startIndex)
                .build();
    }

    private static ElliottScenario buildPartialImpulseScenario(BarSeries series, int startIndex,
            List<PivotCandidate> path, int endIndex, ElliottPhase currentPhase) {
        List<ElliottSwing> swings = new ArrayList<>();
        int fromIndex = startIndex;
        Num fromPrice = pivotPrice(series, startIndex, false);
        for (PivotCandidate candidate : path) {
            swings.add(new ElliottSwing(fromIndex, candidate.barIndex(), fromPrice, candidate.price(),
                    ElliottDegree.PRIMARY));
            fromIndex = candidate.barIndex();
            fromPrice = candidate.price();
        }
        boolean endHighPivot = currentPhase.impulseIndex() % 2 == 1;
        Num endPrice = pivotPrice(series, endIndex, endHighPivot);
        swings.add(new ElliottSwing(fromIndex, endIndex, fromPrice, endPrice, ElliottDegree.PRIMARY));
        return ElliottScenario.builder()
                .id("btc-current-" + currentPhase.name().toLowerCase() + "-" + startIndex + "-" + endIndex)
                .currentPhase(currentPhase)
                .swings(List.copyOf(swings))
                .confidence(confidenceFor(series, List.copyOf(swings), currentPhase))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(pivotPrice(series, startIndex, false))
                .primaryTarget(endPrice)
                .type(ScenarioType.IMPULSE)
                .startIndex(startIndex)
                .build();
    }

    private static ElliottConfidence confidenceFor(BarSeries series, List<ElliottSwing> swings, ElliottPhase phase) {
        Num overall = series.numFactory().numOf(phase.completesStructure() ? 0.75 : 0.60);
        Num structure = series.numFactory().numOf(phase.isImpulse() ? 0.70 : 0.65);
        Num spacing = series.numFactory().numOf(0.60);
        Num alternation = series.numFactory().numOf(0.60);
        Num channel = series.numFactory().numOf(0.55);
        Num completeness = series.numFactory().numOf(phase.completesStructure() ? 1.0 : 0.60);
        return new ElliottConfidence(overall, structure, spacing, alternation, channel, completeness,
                "BTC macro decomposition");
    }

    private static ScenarioType classifyCorrectiveType(int startIndex, List<PivotCandidate> candidates, int endIndex,
            BarSeries series, MacroLogicProfile profile) {
        double start = pivotPrice(series, startIndex, true).doubleValue();
        double waveAEnd = candidates.get(0).price().doubleValue();
        double waveBEnd = candidates.get(1).price().doubleValue();
        double end = pivotPrice(series, endIndex, false).doubleValue();
        double aLength = start - waveAEnd;
        double bRatio = (waveBEnd - waveAEnd) / Math.max(EPSILON, aLength);
        double cRatio = (waveBEnd - end) / Math.max(EPSILON, aLength);
        if (waveBEnd > start && end < waveAEnd) {
            return ScenarioType.CORRECTIVE_COMPLEX;
        }
        if (bRatio > 0.85 || profile.correctiveRelaxation() > 0.50) {
            return ScenarioType.CORRECTIVE_FLAT;
        }
        if (cRatio >= 1.0) {
            return ScenarioType.CORRECTIVE_ZIGZAG;
        }
        return ScenarioType.CORRECTIVE_COMPLEX;
    }

    private static List<BarLabel> buildAnchorLabels(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        List<BarLabel> labels = new ArrayList<>();
        Num topPad = series.numFactory().numOf("1.02");
        Num lowPad = series.numFactory().numOf("0.98");
        for (ElliottWaveAnchorCalibrationHarness.Anchor anchor : registry.anchors()) {
            int barIndex = nearestIndex(series, anchor.at());
            Bar bar = series.getBar(barIndex);
            boolean top = anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            Num yValue = top ? bar.getHighPrice().multipliedBy(topPad) : bar.getLowPrice().multipliedBy(lowPad);
            String date = bar.getEndTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
            String text = top ? "Bullish 1-5 top\n" + date : "Bearish A-C low\n" + date;
            LabelPlacement placement = top ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
            Color labelColor = top ? BULLISH_LEG_COLOR : BEARISH_LEG_COLOR;
            labels.add(new BarLabel(barIndex, yValue, text, placement, labelColor));
        }
        return List.copyOf(labels);
    }

    private static List<BarLabel> buildSegmentWaveLabels(BarSeries series, List<SegmentScenarioFit> segmentFits,
            CurrentPhaseFit currentPrimaryFit) {
        List<BarLabel> rawLabels = new ArrayList<>();
        for (SegmentScenarioFit fit : segmentFits) {
            Color labelColor = fit.accepted() ? (fit.bullish() ? BULLISH_WAVE_COLOR : BEARISH_WAVE_COLOR)
                    : (fit.bullish() ? BULLISH_CANDIDATE_COLOR : BEARISH_CANDIDATE_COLOR);
            rawLabels.addAll(buildWaveLabelsFromScenario(series, fit.scenario(), labelColor));
        }
        if (currentPrimaryFit != null) {
            rawLabels.addAll(buildWaveLabelsFromScenario(series, currentPrimaryFit.scenario(), BULLISH_WAVE_COLOR));
        }
        return deconflictLabels(series, rawLabels);
    }

    private static List<BarLabel> deconflictLabels(BarSeries series, List<BarLabel> labels) {
        List<BarLabel> ordered = labels.stream().sorted(Comparator.comparingInt(BarLabel::barIndex)).toList();
        List<BarLabel> adjusted = new ArrayList<>();
        for (BarLabel label : ordered) {
            int clusterDepth = 0;
            for (int index = adjusted.size() - 1; index >= 0; index--) {
                BarLabel prior = adjusted.get(index);
                if (label.barIndex() - prior.barIndex() > LABEL_CLUSTER_BAR_GAP) {
                    break;
                }
                if (prior.placement() == label.placement()) {
                    clusterDepth++;
                }
            }
            Num adjustedY = applyLabelClusterOffset(series, label, clusterDepth);
            adjusted.add(new BarLabel(label.barIndex(), adjustedY, label.text(), label.placement(), label.color()));
        }
        return List.copyOf(adjusted);
    }

    private static Num applyLabelClusterOffset(BarSeries series, BarLabel label, int clusterDepth) {
        if (clusterDepth == 0) {
            return label.yValue();
        }
        double step = 0.04 * clusterDepth;
        double multiplier = switch (label.placement()) {
        case ABOVE -> 1.0 + step;
        case BELOW -> 1.0 - step;
        case CENTER -> 1.0;
        };
        return label.yValue().multipliedBy(series.numFactory().numOf(multiplier));
    }

    private static FixedIndicator<Num> buildScenarioFitIndicator(BarSeries series, List<SegmentScenarioFit> segmentFits,
            boolean bullish, boolean accepted, String label) {
        Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        for (SegmentScenarioFit fit : segmentFits) {
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

    private static FixedIndicator<Num> buildScenarioIndicator(BarSeries series, ElliottScenario scenario,
            String label) {
        Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        if (scenario != null) {
            applyScenarioPath(values, series, scenario);
        }
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static void applyScenarioPath(Num[] values, BarSeries series, ElliottScenario scenario) {
        for (ElliottSwing swing : scenario.swings()) {
            int fromIndex = Math.max(series.getBeginIndex(), Math.min(swing.fromIndex(), swing.toIndex()));
            int toIndex = Math.min(series.getEndIndex(), Math.max(swing.fromIndex(), swing.toIndex()));
            if (toIndex < fromIndex) {
                continue;
            }
            double fromPrice = swing.fromPrice().doubleValue();
            double toPrice = swing.toPrice().doubleValue();
            int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                double progress = (double) (index - fromIndex) / length;
                double interpolated = fromPrice + ((toPrice - fromPrice) * progress);
                values[index] = series.numFactory().numOf(interpolated);
            }
        }
    }

    private static FixedIndicator<Num> buildCycleLegIndicator(BarSeries series, List<LegSegment> legSegments,
            boolean bullish, String label, int currentCycleStartIndex) {
        Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        for (LegSegment legSegment : legSegments) {
            if (legSegment.bullish() != bullish) {
                continue;
            }
            ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor = legSegment.fromAnchor();
            ElliottWaveAnchorCalibrationHarness.Anchor toAnchor = legSegment.toAnchor();
            int fromIndex = nearestIndex(series, fromAnchor.at());
            int toIndex = nearestIndex(series, toAnchor.at());
            if (toIndex < fromIndex) {
                continue;
            }

            double fromPrice = anchorPrice(series, fromIndex, fromAnchor.type());
            double toPrice = anchorPrice(series, toIndex, toAnchor.type());
            int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                double progress = (double) (index - fromIndex) / length;
                double interpolated = fromPrice + ((toPrice - fromPrice) * progress);
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

    private static void clipCurrentCycleMacroGuide(Num[] values, int currentCycleStartIndex) {
        if (currentCycleStartIndex == Integer.MAX_VALUE) {
            return;
        }
        int clipFromIndex = Math.max(0, currentCycleStartIndex + 1);
        for (int index = clipFromIndex; index < values.length; index++) {
            values[index] = NaN;
        }
    }

    private static List<LegSegment> buildLegSegments(ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = registry.anchors()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .toList();
        List<LegSegment> legSegments = new ArrayList<>();
        for (int index = 1; index < anchors.size(); index++) {
            ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor = anchors.get(index - 1);
            ElliottWaveAnchorCalibrationHarness.Anchor toAnchor = anchors.get(index);
            if (fromAnchor.type() == toAnchor.type()) {
                continue;
            }
            boolean bullish = fromAnchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM
                    && toAnchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            legSegments.add(new LegSegment(fromAnchor, toAnchor, bullish));
        }
        return List.copyOf(legSegments);
    }

    private static List<MacroCycle> buildHistoricalCycles(ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = registry.anchors()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .toList();
        List<MacroCycle> cycles = new ArrayList<>();
        for (int index = 0; index <= anchors.size() - 3; index++) {
            ElliottWaveAnchorCalibrationHarness.Anchor start = anchors.get(index);
            ElliottWaveAnchorCalibrationHarness.Anchor peak = anchors.get(index + 1);
            ElliottWaveAnchorCalibrationHarness.Anchor low = anchors.get(index + 2);
            if (start.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM
                    || peak.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.TOP
                    || low.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM) {
                continue;
            }
            String partition = low.partition().name().toLowerCase();
            LegSegment bullishLeg = new LegSegment(start, peak, true);
            LegSegment bearishLeg = new LegSegment(peak, low, false);
            cycles.add(new MacroCycle(start.id() + "->" + peak.id() + "->" + low.id(), partition, start, peak, low,
                    bullishLeg, bearishLeg));
        }
        return List.copyOf(cycles);
    }

    private static void applyLogPriceAxis(JFreeChart chart, BarSeries series) {
        if (!(chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot)) {
            return;
        }
        if (combinedPlot.getSubplots() == null || combinedPlot.getSubplots().isEmpty()) {
            return;
        }

        Object subplot = combinedPlot.getSubplots().getFirst();
        if (!(subplot instanceof XYPlot pricePlot)) {
            return;
        }

        LogAxis logAxis = new LogAxis("Price (USD, log)");
        logAxis.setAutoRange(true);
        logAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        logAxis.setLabelPaint(Color.LIGHT_GRAY);
        logAxis.setSmallestValue(smallestPositiveLow(series));
        pricePlot.setRangeAxis(logAxis);
    }

    private static double smallestPositiveLow(BarSeries series) {
        double smallest = Double.POSITIVE_INFINITY;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            double low = series.getBar(index).getLowPrice().doubleValue();
            if (Double.isFinite(low) && low > 0.0 && low < smallest) {
                smallest = low;
            }
        }
        return Double.isFinite(smallest) ? smallest : 1.0;
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor findAnchor(
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, String anchorId) {
        for (ElliottWaveAnchorCalibrationHarness.Anchor anchor : registry.anchors()) {
            if (anchor.id().equals(anchorId)) {
                return anchor;
            }
        }
        throw new IllegalArgumentException("Unknown BTC anchor id: " + anchorId);
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor findAnchorOrLatestBottom(
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, String anchorId) {
        for (ElliottWaveAnchorCalibrationHarness.Anchor anchor : registry.anchors()) {
            if (anchor.id().equals(anchorId)) {
                return anchor;
            }
        }
        return registry.anchors()
                .stream()
                .filter(anchor -> anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM)
                .max(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .orElseThrow(
                        () -> new IllegalArgumentException("No bottom anchor available for current-cycle analysis"));
    }

    private static String segmentKey(LegSegment legSegment) {
        return legSegment.fromAnchor().id() + "->" + legSegment.toAnchor().id();
    }

    private static double segmentRange(BarSeries series, int startIndex, int endIndex) {
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (int index = startIndex; index <= endIndex; index++) {
            lowest = Math.min(lowest, series.getBar(index).getLowPrice().doubleValue());
            highest = Math.max(highest, series.getBar(index).getHighPrice().doubleValue());
        }
        return Math.max(EPSILON, highest - lowest);
    }

    private static double weightedScore(double structureScore, double ruleScore, double spacingScore,
            double strengthScore, MacroLogicProfile profile) {
        return clamp(
                (profile.structureWeight() * structureScore) + (profile.ruleWeight() * ruleScore)
                        + (profile.spacingWeight() * spacingScore) + (profile.strengthWeight() * strengthScore),
                0.0, 1.0);
    }

    private static double spacingScore(int startIndex, int endIndex, List<PivotCandidate> candidates,
            double[] expectedFractions) {
        List<Integer> indices = new ArrayList<>();
        indices.add(startIndex);
        for (PivotCandidate candidate : candidates) {
            indices.add(candidate.barIndex());
        }
        indices.add(endIndex);
        double durationBalance = durationBalanceScore(indices);
        double fractionScore = fractionAlignmentScore(indices, startIndex, endIndex, expectedFractions);
        return average(new double[] { durationBalance, fractionScore }, 0.0);
    }

    private static double durationBalanceScore(List<Integer> indices) {
        if (indices.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        int min = Integer.MAX_VALUE;
        for (int index = 1; index < indices.size(); index++) {
            int duration = indices.get(index) - indices.get(index - 1);
            min = Math.min(min, duration);
            sum += duration;
        }
        double average = sum / (indices.size() - 1);
        return clamp(min / Math.max(1.0, average), 0.0, 1.0);
    }

    private static double fractionAlignmentScore(List<Integer> indices, int startIndex, int endIndex,
            double[] expectedFractions) {
        if (expectedFractions.length == 0) {
            return 1.0;
        }
        double segmentLength = Math.max(1.0, endIndex - startIndex);
        double total = 0.0;
        int usable = Math.min(expectedFractions.length, indices.size() - 2);
        for (int index = 0; index < usable; index++) {
            double actualFraction = (indices.get(index + 1) - startIndex) / segmentLength;
            total += 1.0 - Math.min(1.0, Math.abs(actualFraction - expectedFractions[index]));
        }
        return usable == 0 ? 0.0 : clamp(total / usable, 0.0, 1.0);
    }

    private static double boundedScore(double value, double targetMin, double targetMax, double tolerance) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        if (value >= targetMin && value <= targetMax) {
            return 1.0;
        }
        if (value < targetMin) {
            return clamp(1.0 - ((targetMin - value) / Math.max(EPSILON, tolerance)), 0.0, 1.0);
        }
        return clamp(1.0 - ((value - targetMax) / Math.max(EPSILON, tolerance)), 0.0, 1.0);
    }

    private static double average(double[] values, double fallback) {
        if (values.length == 0) {
            return fallback;
        }
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static int minimumSwingGap(int startIndex, int endIndex, int waveCount) {
        return Math.max(2, (endIndex - startIndex) / Math.max(8, waveCount * 5));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampIndex(int index, int min, int max) {
        return Math.max(min, Math.min(max, index));
    }

    private static double anchorPrice(BarSeries series, int barIndex,
            ElliottWaveAnchorCalibrationHarness.AnchorType anchorType) {
        Bar bar = series.getBar(barIndex);
        return anchorType == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? bar.getHighPrice().doubleValue()
                : bar.getLowPrice().doubleValue();
    }

    private static Num pivotPrice(BarSeries series, int barIndex, boolean highPivot) {
        return highPivot ? series.getBar(barIndex).getHighPrice() : series.getBar(barIndex).getLowPrice();
    }

    private static String waveLabelForPhase(ElliottPhase phase, int waveIndex) {
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

    private static LabelPlacement placementForPivot(boolean highPivot) {
        return highPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    private static Num offsetLabelValue(BarSeries series, Num pivotPrice, boolean highPivot) {
        Num multiplier = highPivot ? series.numFactory().numOf("1.02") : series.numFactory().numOf("0.98");
        return pivotPrice.multipliedBy(multiplier);
    }

    private static int nearestIndex(BarSeries series, Instant target) {
        int nearest = series.getBeginIndex();
        long bestDistance = Long.MAX_VALUE;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            long distance = Math.abs(series.getBar(index).getEndTime().toEpochMilli() - target.toEpochMilli());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static BarSeries requireSeries(String resource, String seriesName) {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class, resource,
                seriesName, LOG);
        if (series == null) {
            throw new IllegalStateException("Unable to load required resource " + resource);
        }
        return series;
    }

    private static void saveSummary(DemoReport report, Path summaryPath) {
        try {
            Files.createDirectories(summaryPath.getParent());
            Files.writeString(summaryPath, report.toJson());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write BTC macro-cycle summary " + summaryPath, exception);
        }
    }

    private static List<MacroLogicProfile> logicProfiles() {
        return List.of(
                new MacroLogicProfile("orthodox-classical", "H0", "Classical Elliott constraints", 0,
                        ElliottDegree.MINOR, 1, 1, 25, 0, 2, PatternSet.all(), 0.70, false, new int[] { 21, 55, 89 },
                        new double[] { 0.18, 0.36, 0.64, 0.82 }, new double[] { 0.32, 0.60 }, 0.0, 0.0, 0.55, 0.38,
                        0.34, 0.18, 0.10, 0.74),
                new MacroLogicProfile("h1-hierarchical-swing", "H1", "Hierarchical swing extraction", 1,
                        ElliottDegree.MINOR, 1, 1, 25, 0, 4, PatternSet.all(), 0.78, false,
                        new int[] { 13, 34, 89, 144 }, new double[] { 0.18, 0.36, 0.64, 0.82 },
                        new double[] { 0.32, 0.60 }, 0.0, 0.0, 0.70, 0.36, 0.32, 0.18, 0.14, 0.72),
                new MacroLogicProfile("h2-btc-relaxed-impulse", "H2", "Relaxed impulse rules for BTC", 2,
                        ElliottDegree.MINOR, 1, 1, 35, 0, 4,
                        PatternSet.of(ScenarioType.IMPULSE, ScenarioType.CORRECTIVE_ZIGZAG,
                                ScenarioType.CORRECTIVE_FLAT),
                        0.82, false, new int[] { 13, 34, 89, 144 }, new double[] { 0.16, 0.35, 0.63, 0.83 },
                        new double[] { 0.32, 0.60 }, 0.55, 0.0, 0.72, 0.36, 0.28, 0.20, 0.16, 0.70),
                new MacroLogicProfile("h3-btc-relaxed-corrective", "H3", "Relaxed corrective coverage for BTC", 3,
                        ElliottDegree.MINOR, 1, 1, 35, 0, 5, PatternSet.all(), 0.64, true,
                        new int[] { 13, 34, 89, 144 }, new double[] { 0.16, 0.35, 0.63, 0.83 },
                        new double[] { 0.28, 0.58 }, 0.55, 0.65, 0.72, 0.34, 0.28, 0.20, 0.18, 0.68),
                new MacroLogicProfile("h4-anchor-first-hybrid", "H4", "Anchor-first hybrid profile", 4,
                        ElliottDegree.MINOR, 2, 2, 40, 0, 5, PatternSet.all(), 0.58, true,
                        new int[] { 8, 21, 55, 144, 233 }, new double[] { 0.15, 0.34, 0.63, 0.84 },
                        new double[] { 0.27, 0.57 }, 0.75, 0.80, 0.90, 0.30, 0.24, 0.30, 0.16, 0.66));
    }

    private static String formatScore(double score) {
        return String.format(java.util.Locale.ROOT, "%.3f", score);
    }

    private static String formatNum(Num value) {
        return value == null ? "" : value.toString();
    }

    private static String bullishCountLabel(int phase) {
        return switch (phase) {
        case 1 -> "Bullish 1";
        case 2 -> "Bullish 1-2";
        case 3 -> "Bullish 1-2-3";
        case 4 -> "Bullish 1-2-3-4";
        case 5 -> "Bullish 1-2-3-4-5";
        default -> "Bullish";
        };
    }

    private static Map<String, String> orderedEvidence(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must contain complete key/value pairs");
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            ordered.put(entries[index], entries[index + 1]);
        }
        return Map.copyOf(ordered);
    }

    record DemoReport(String registryVersion, String datasetResource, String baselineProfileId,
            String selectedProfileId, String selectedHypothesisId, boolean historicalFitPassed,
            String harnessDecisionRationale, String chartPath, String summaryPath,
            List<ProfileScoreSummary> profileScores, List<DirectionalCycleSummary> cycles,
            List<HypothesisResult> hypotheses, CurrentCycleSummary currentCycle) {

        DemoReport {
            Objects.requireNonNull(registryVersion, "registryVersion");
            Objects.requireNonNull(datasetResource, "datasetResource");
            Objects.requireNonNull(baselineProfileId, "baselineProfileId");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(selectedHypothesisId, "selectedHypothesisId");
            Objects.requireNonNull(harnessDecisionRationale, "harnessDecisionRationale");
            Objects.requireNonNull(chartPath, "chartPath");
            Objects.requireNonNull(summaryPath, "summaryPath");
            profileScores = profileScores == null ? List.of() : List.copyOf(profileScores);
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
            Objects.requireNonNull(currentCycle, "currentCycle");
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    record ProfileScoreSummary(String profileId, String hypothesisId, String title, double aggregateScore,
            int acceptedCycles, int acceptedSegments, boolean historicalFitPassed) {

        static ProfileScoreSummary from(MacroProfileEvaluation evaluation) {
            return new ProfileScoreSummary(evaluation.profile().id(), evaluation.profile().hypothesisId(),
                    evaluation.profile().title(), evaluation.aggregateScore(), evaluation.acceptedCycles(),
                    evaluation.acceptedSegments(), evaluation.historicalFitPassed());
        }
    }

    record DirectionalCycleSummary(String partition, String cycleId, String impulseLabel, String peakLabel,
            String correctionLabel, String lowLabel, String startTimeUtc, String peakTimeUtc, String lowTimeUtc,
            double bullishScore, double bearishScore, boolean bullishAccepted, boolean bearishAccepted,
            boolean accepted, String status) {

        static DirectionalCycleSummary from(CycleFit cycleFit) {
            MacroCycle cycle = cycleFit.cycle();
            String status;
            if (cycleFit.accepted()) {
                status = "accepted historical fit";
            } else if (cycleFit.bullishFit() != null && cycleFit.bullishFit().accepted()) {
                status = "bullish fit only";
            } else if (cycleFit.bearishFit() != null && cycleFit.bearishFit().accepted()) {
                status = "bearish fit only";
            } else {
                status = "fallback or miss";
            }
            return new DirectionalCycleSummary(cycle.partition(), cycle.id(), "Bullish 1-2-3-4-5", "Bullish WAVE5 top",
                    "Bearish A-B-C", "Bearish CORRECTIVE_C low", cycle.start().at().toString(),
                    cycle.peak().at().toString(), cycle.low().at().toString(),
                    cycleFit.bullishFit() == null ? 0.0 : cycleFit.bullishFit().fitScore(),
                    cycleFit.bearishFit() == null ? 0.0 : cycleFit.bearishFit().fitScore(),
                    cycleFit.bullishFit() != null && cycleFit.bullishFit().accepted(),
                    cycleFit.bearishFit() != null && cycleFit.bearishFit().accepted(), cycleFit.accepted(), status);
        }
    }

    record HypothesisResult(String id, String title, boolean supported, String summary, Map<String, String> evidence) {

        HypothesisResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            evidence = evidence == null ? Map.of() : new TreeMap<>(evidence);
        }
    }

    record CurrentCycleSummary(String startTimeUtc, String latestTimeUtc, String winningProfileId,
            String historicalStatus, String primaryCount, String alternateCount, String currentWave,
            String invalidationPrice, double primaryScore, double alternateScore, String chartPath) {

        CurrentCycleSummary withChartPath(String newChartPath) {
            return new CurrentCycleSummary(startTimeUtc, latestTimeUtc, winningProfileId, historicalStatus,
                    primaryCount, alternateCount, currentWave, invalidationPrice, primaryScore, alternateScore,
                    newChartPath);
        }
    }

    record CurrentCycleAnalysis(CurrentCycleSummary summary, CurrentPhaseFit primaryFit, CurrentPhaseFit alternateFit) {

        CurrentCycleAnalysis {
            Objects.requireNonNull(summary, "summary");
        }
    }

    record MacroStudy(MacroProfileEvaluation selectedProfile, List<MacroProfileEvaluation> evaluations,
            List<ProfileScoreSummary> profileScores, List<DirectionalCycleSummary> cycles,
            List<HypothesisResult> hypotheses, CurrentCycleSummary currentCycle, CurrentPhaseFit currentPrimaryFit,
            CurrentPhaseFit currentAlternateFit) {

        MacroStudy {
            Objects.requireNonNull(selectedProfile, "selectedProfile");
            evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
            profileScores = profileScores == null ? List.of() : List.copyOf(profileScores);
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
            Objects.requireNonNull(currentCycle, "currentCycle");
        }
    }

    record MacroProfileEvaluation(MacroLogicProfile profile, double aggregateScore, int acceptedCycles,
            int acceptedSegments, boolean historicalFitPassed, List<CycleFit> cycleFits,
            List<SegmentScenarioFit> chartSegments) {

        MacroProfileEvaluation {
            Objects.requireNonNull(profile, "profile");
            cycleFits = cycleFits == null ? List.of() : List.copyOf(cycleFits);
            chartSegments = chartSegments == null ? List.of() : List.copyOf(chartSegments);
        }
    }

    record MacroLogicProfile(String id, String hypothesisId, String title, int orthodoxyRank,
            ElliottDegree runnerDegree, int runnerHigherDegrees, int runnerLowerDegrees, int runnerMaxScenarios,
            int runnerScenarioSwingWindow, int runnerFractalWindow, PatternSet patternSet,
            double runnerBaseConfidenceWeight, boolean patternAwareConfidence, int[] pivotRadii,
            double[] impulseFractions, double[] correctiveFractions, double impulseRelaxation,
            double correctiveRelaxation, double expectedFractionWeight, double structureWeight, double ruleWeight,
            double spacingWeight, double strengthWeight, double acceptanceThreshold) {

        MacroLogicProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hypothesisId, "hypothesisId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(runnerDegree, "runnerDegree");
            Objects.requireNonNull(patternSet, "patternSet");
            pivotRadii = pivotRadii == null ? new int[0] : pivotRadii.clone();
            impulseFractions = impulseFractions == null ? new double[0] : impulseFractions.clone();
            correctiveFractions = correctiveFractions == null ? new double[0] : correctiveFractions.clone();
        }
    }

    record MacroCycle(String id, String partition, ElliottWaveAnchorCalibrationHarness.Anchor start,
            ElliottWaveAnchorCalibrationHarness.Anchor peak, ElliottWaveAnchorCalibrationHarness.Anchor low,
            LegSegment bullishLeg, LegSegment bearishLeg) {
    }

    record CycleFit(MacroCycle cycle, SegmentScenarioFit bullishFit, SegmentScenarioFit bearishFit,
            double aggregateScore, boolean accepted) {

        static CycleFit create(MacroCycle cycle, SegmentScenarioFit bullishFit, SegmentScenarioFit bearishFit) {
            double bullishScore = bullishFit == null ? 0.0 : bullishFit.fitScore();
            double bearishScore = bearishFit == null ? 0.0 : bearishFit.fitScore();
            double aggregate = (bullishScore + bearishScore) / 2.0;
            boolean accepted = bullishFit != null && bullishFit.accepted() && bearishFit != null
                    && bearishFit.accepted();
            return new CycleFit(cycle, bullishFit, bearishFit, aggregate, accepted);
        }
    }

    record LegSegment(ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor,
            ElliottWaveAnchorCalibrationHarness.Anchor toAnchor, boolean bullish) {

        LegSegment {
            Objects.requireNonNull(fromAnchor, "fromAnchor");
            Objects.requireNonNull(toAnchor, "toAnchor");
        }
    }

    record SegmentScenarioFit(LegSegment segment, ElliottScenario scenario, double fitScore, double structureScore,
            double ruleScore, double spacingScore, double strengthScore, boolean bullish, boolean accepted,
            String rationale) implements Comparable<SegmentScenarioFit> {

        @Override
        public int compareTo(SegmentScenarioFit other) {
            int acceptedComparison = Boolean.compare(other.accepted, accepted);
            if (acceptedComparison != 0) {
                return acceptedComparison;
            }
            int scoreComparison = Double.compare(other.fitScore, fitScore);
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            return scenario.id().compareTo(other.scenario.id());
        }

        boolean eyeballPass() {
            return accepted;
        }
    }

    record CurrentPhaseFit(ElliottScenario scenario, ElliottPhase currentPhase, double fitScore, Num startPrice,
            String countLabel, Num invalidationPrice) implements Comparable<CurrentPhaseFit> {

        @Override
        public int compareTo(CurrentPhaseFit other) {
            return Double.compare(other.fitScore, fitScore);
        }
    }

    record PivotCandidate(int barIndex, Num price, double rawScore, double normalizedScore) {
    }

    private static final class CandidateAccumulator {

        private final int barIndex;
        private final Num price;
        private double rawScore;

        private CandidateAccumulator(int barIndex, Num price) {
            this.barIndex = barIndex;
            this.price = price;
        }

        private void add(double score) {
            this.rawScore += score;
        }

        private PivotCandidate toCandidate(int startIndex, int endIndex) {
            double segmentLength = Math.max(1.0, endIndex - startIndex);
            double normalized = clamp(rawScore / Math.max(1.0, segmentLength / 25.0), 0.0, 1.0);
            return new PivotCandidate(barIndex, price, rawScore, normalized);
        }
    }
}

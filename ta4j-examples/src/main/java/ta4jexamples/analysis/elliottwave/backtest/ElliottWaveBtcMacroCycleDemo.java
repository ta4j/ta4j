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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    static final String LIVE_RESULT_PREFIX = "EW_BTC_LIVE_MACRO: ";
    static final Path DEFAULT_CHART_DIRECTORY = Path.of("temp", "charts");
    static final String DEFAULT_CHART_FILE_NAME = "elliott-wave-btc-macro-cycles";
    static final String DEFAULT_SUMMARY_FILE_NAME = "elliott-wave-btc-macro-cycles-summary.json";
    static final String DEFAULT_LIVE_CHART_FILE_NAME = "elliott-wave-btc-live-macro-current-cycle";
    static final String DEFAULT_LIVE_SUMMARY_FILE_NAME = "elliott-wave-btc-live-macro-current-cycle-summary.json";
    static final int DEFAULT_CHART_WIDTH = 3840;
    static final int DEFAULT_CHART_HEIGHT = 2160;
    static final int MIN_CORE_SEGMENT_SCENARIOS = 1000;
    static final int MAX_CORE_ANCHOR_DRIFT_BARS = 3;
    static final double DEFAULT_ACCEPTED_SEGMENT_SCORE = 0.64;
    static final int LABEL_CLUSTER_BAR_GAP = 18;
    static final Color BULLISH_LEG_COLOR = new Color(0x66BB6A);
    static final Color BEARISH_LEG_COLOR = new Color(0xEF5350);
    static final Color BULLISH_WAVE_COLOR = new Color(0x81C784);
    static final Color BEARISH_WAVE_COLOR = new Color(0xE57373);
    static final Color BULLISH_CANDIDATE_COLOR = new Color(0xC8E6C9);
    static final Color BEARISH_CANDIDATE_COLOR = new Color(0xFFCDD2);
    static final Color ANCHOR_OVERLAY_COLOR = new Color(0xCFD8DC);
    static final double WAVE_LABEL_FONT_SCALE = 3.0;

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

    /**
     * Runs the series-native live BTC macro preset on the supplied series and logs
     * the resulting JSON summary.
     *
     * <p>
     * Unlike the historical anchor-validation report, this entry point discovers
     * the current-cycle start from the supplied series itself. That keeps the live
     * preset causal with respect to the provided bars instead of assuming access to
     * a longer external anchor history.
     *
     * @param series         live or loaded BTC series to analyze
     * @param chartDirectory directory for the saved current-cycle chart and JSON
     *                       summary
     * @since 0.22.4
     */
    public static void runLivePreset(BarSeries series, Path chartDirectory) {
        LivePresetAnalysis analysis = analyzeLivePreset(series, chartDirectory);
        LivePresetLegacyView legacyView = generateLivePresetLegacyView(series, analysis, chartDirectory);
        logLegacyCompatibleLivePreset(legacyView);
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

    static LivePresetReport generateLivePresetReport(BarSeries series, Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        return analyzeLivePreset(series, chartDirectory).report();
    }

    private static LivePresetAnalysis analyzeLivePreset(BarSeries series, Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        MacroLogicProfile profile = defaultLiveMacroProfile();
        CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, profile,
                "BTC macro profile prevalidated from historical cycle truth set");
        Optional<Path> chartPath = saveLiveCurrentCycleChart(series, currentCycle, chartDirectory);
        String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        Path summaryPath = chartDirectory.resolve(DEFAULT_LIVE_SUMMARY_FILE_NAME).toAbsolutePath().normalize();
        CurrentCycleSummary summary = currentCycle.summary().withChartPath(chartPathText);
        LivePresetReport report = new LivePresetReport(series.getName(), series.getFirstBar().getEndTime().toString(),
                series.getLastBar().getEndTime().toString(), profile.id(), profile.hypothesisId(), chartPathText,
                summaryPath.toString(), summary);
        saveSummary(report, summaryPath);
        return new LivePresetAnalysis(profile, currentCycle.withSummary(summary), report);
    }

    static Optional<Path> saveMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, Path chartDirectory) {
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

    private static Optional<Path> saveLiveCurrentCycleChart(BarSeries series, CurrentCycleAnalysis currentCycle,
            Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(currentCycle, "currentCycle");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        if (currentCycle.primaryFit() == null) {
            return Optional.empty();
        }

        ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        JFreeChart chart = renderLiveCurrentCycleChart(series, currentCycle);
        return chartWorkflow.saveChartImage(chart, series, DEFAULT_LIVE_CHART_FILE_NAME, DEFAULT_CHART_WIDTH,
                DEFAULT_CHART_HEIGHT);
    }

    private static LivePresetLegacyView generateLivePresetLegacyView(BarSeries series, LivePresetAnalysis analysis,
            Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        List<ElliottWaveAnalysisResult.CurrentCycleCandidate> displayCandidates = selectDisplayCandidates(
                analysis.currentCycle().candidates(), 5);
        List<LivePresetScenarioView> scenarioViews = buildLivePresetScenarioViews(displayCandidates);
        saveLegacyCompatibleScenarioCharts(series, scenarioViews, chartDirectory, ElliottDegree.CYCLE);
        return new LivePresetLegacyView(analysis.report(), scenarioViews);
    }

    private static List<ElliottWaveAnalysisResult.CurrentCycleCandidate> selectDisplayCandidates(
            List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates, int max) {
        if (candidates == null || candidates.isEmpty() || max <= 0) {
            return List.of();
        }

        List<ElliottWaveAnalysisResult.CurrentCycleCandidate> selected = new ArrayList<>(
                Math.min(max, candidates.size()));
        Set<String> seenScenarioIds = new HashSet<>();
        for (ElliottWaveAnalysisResult.CurrentCycleCandidate candidate : candidates) {
            String scenarioId = candidate.fit().scenario().id();
            if (!seenScenarioIds.add(scenarioId)) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() == max) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private static List<LivePresetScenarioView> buildLivePresetScenarioViews(
            List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double totalScore = candidates.stream()
                .mapToDouble(ElliottWaveAnalysisResult.CurrentCycleCandidate::totalScore)
                .sum();
        List<LivePresetScenarioView> scenarioViews = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            ElliottWaveAnalysisResult.CurrentCycleCandidate candidate = candidates.get(index);
            double probability = totalScore <= EPSILON ? 0.0 : candidate.totalScore() / totalScore;
            String label = index == 0 ? "BASE CASE" : "ALTERNATIVE " + index;
            String fileName = index == 0 ? livePresetChartBaseCaseFileName(candidate.fit().scenario())
                    : livePresetChartAlternativeFileName(candidate.fit().scenario(), index);
            scenarioViews.add(new LivePresetScenarioView(label, fileName, probability, candidate));
        }
        return List.copyOf(scenarioViews);
    }

    private static void saveLegacyCompatibleScenarioCharts(BarSeries series, List<LivePresetScenarioView> scenarioViews,
            Path chartDirectory, ElliottDegree degree) {
        if (scenarioViews.isEmpty()) {
            return;
        }

        ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        boolean isHeadless = GraphicsEnvironment.isHeadless();
        String trendLabel = formatLegacyTrendLabel(scenarioViews);
        for (LivePresetScenarioView scenarioView : scenarioViews) {
            ChartPlan plan = buildLegacyCompatibleLiveScenarioPlan(series, scenarioView, trendLabel, degree);
            if (!isHeadless) {
                chartWorkflow.display(plan, buildLegacyScenarioWindowTitle(degree, trendLabel, scenarioView.label(),
                        scenarioView.candidate().fit().scenario(), series.getName()));
            }
            chartWorkflow.save(plan, scenarioView.fileName(), DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT);
        }
    }

    private static String formatLegacyTrendLabel(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "TREND: UNKNOWN";
        }

        long bullishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> view.candidate().fit().scenario().isBullish())
                .count();
        long bearishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> !view.candidate().fit().scenario().isBullish())
                .count();
        if (bullishCount == bearishCount) {
            return "TREND: NEUTRAL";
        }
        return bullishCount > bearishCount ? "TREND: BULLISH" : "TREND: BEARISH";
    }

    private static ChartPlan buildLegacyCompatibleLiveScenarioPlan(BarSeries series,
            LivePresetScenarioView scenarioView, String trendLabel, ElliottDegree degree) {
        ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = scenarioView.candidate().fit();
        Color scenarioColor = fit.scenario().isBullish() ? BULLISH_WAVE_COLOR : BEARISH_WAVE_COLOR;
        BarSeriesLabelIndicator labels = new BarSeriesLabelIndicator(series,
                buildWaveLabelsFromScenario(series, fit.scenario(), scenarioColor));
        FixedIndicator<Num> scenarioPath = buildScenarioIndicator(series, fit.scenario(), fit.countLabel());
        ChartWorkflow chartWorkflow = new ChartWorkflow();
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

    private static void logLegacyCompatibleLivePreset(LivePresetLegacyView legacyView) {
        Objects.requireNonNull(legacyView, "legacyView");

        LivePresetReport report = legacyView.report();
        CurrentCycleSummary summary = report.currentCycle();
        List<LivePresetScenarioView> scenarioViews = legacyView.scenarioViews();

        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", summarizeLegacyScenarioViews(scenarioViews));
        LOG.info("Strong consensus: {} | Consensus phase: {}", hasStrongConsensus(scenarioViews),
                consensusPhase(scenarioViews).map(Enum::name).orElse("NONE"));
        LOG.info("Trend bias: {}", formatLegacyTrendLabel(scenarioViews));
        LOG.info("Historical status: {}", summary.historicalStatus());
        if (!scenarioViews.isEmpty()) {
            LivePresetScenarioView baseCase = scenarioViews.getFirst();
            ElliottScenario scenario = baseCase.candidate().fit().scenario();
            ElliottConfidence confidence = scenario.confidence();
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
            LOG.info("  Direction: {} | Invalidation: {} | Target: {}",
                    scenario.hasKnownDirection() ? (scenario.isBullish() ? "BULLISH" : "BEARISH") : "UNKNOWN",
                    scenario.invalidationPrice(), scenario.primaryTarget());
        }

        if (scenarioViews.size() > 1) {
            LOG.info("ALTERNATIVE SCENARIOS ({}):", scenarioViews.size() - 1);
            for (int index = 1; index < scenarioViews.size(); index++) {
                LivePresetScenarioView alternative = scenarioViews.get(index);
                ElliottScenario scenario = alternative.candidate().fit().scenario();
                LOG.info("  {}. {} ({}) - {}% confidence | raw={}%, calibrated={}%", index, scenario.currentPhase(),
                        scenario.type(),
                        String.format(java.util.Locale.ROOT, "%.1f", scenario.confidence().asPercentage()),
                        String.format(java.util.Locale.ROOT, "%.1f", alternative.probability() * 100.0),
                        String.format(java.util.Locale.ROOT, "%.1f", alternative.probability() * 100.0));
            }
        }
        LOG.info("Current macro read: primary={} | alternate={} | currentWave={} | invalidation={}",
                summary.primaryCount(), summary.alternateCount(), summary.currentWave(), summary.invalidationPrice());
        LOG.info("Macro summary JSON: {}", report.summaryPath());
        LOG.info("Macro current-cycle chart: {}", report.chartPath());
        LOG.info("======================================");
    }

    private static String summarizeLegacyScenarioViews(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "No scenarios";
        }
        LivePresetScenarioView baseCase = scenarioViews.getFirst();
        StringBuilder summary = new StringBuilder();
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
        Optional<ElliottPhase> consensus = consensusPhase(scenarioViews);
        consensus.ifPresent(phase -> summary.append(", consensus=").append(phase));
        return summary.toString();
    }

    private static boolean hasStrongConsensus(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return false;
        }
        if (scenarioViews.size() == 1) {
            return true;
        }
        double spread = scenarioViews.getFirst().probability() - scenarioViews.get(1).probability();
        return spread >= 0.08;
    }

    private static Optional<ElliottPhase> consensusPhase(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return Optional.empty();
        }

        Map<ElliottPhase, Double> phaseWeights = new LinkedHashMap<>();
        for (LivePresetScenarioView scenarioView : scenarioViews) {
            ElliottScenario scenario = scenarioView.candidate().fit().scenario();
            phaseWeights.merge(scenario.currentPhase(), scenarioView.probability(), Double::sum);
        }
        Map.Entry<ElliottPhase, Double> bestPhase = phaseWeights.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (bestPhase == null || bestPhase.getValue() < 0.60) {
            return Optional.empty();
        }
        return Optional.of(bestPhase.getKey());
    }

    private static String buildLegacyScenarioTitle(ElliottDegree degree, BarSeries series, String trendLabel,
            String scenarioLabel, ElliottScenario scenario) {
        return String.format(java.util.Locale.ROOT, "Elliott Wave (%s) - %s - %s - %s: %s (%s) - %.1f%% confidence",
                degree, series.getName(), trendLabel, scenarioLabel, scenario.currentPhase(), scenario.type(),
                scenario.confidence().asPercentage());
    }

    private static String buildLegacyScenarioWindowTitle(ElliottDegree degree, String trendLabel, String scenarioLabel,
            ElliottScenario scenario, String seriesName) {
        return String.format(java.util.Locale.ROOT, "%s - %s - %s: %s (%s) - %.1f%% - %s", degree, trendLabel,
                scenarioLabel, scenario.currentPhase(), scenario.type(), scenario.confidence().asPercentage(),
                seriesName);
    }

    private static String livePresetChartBaseCaseFileName(ElliottScenario scenario) {
        return "elliott-wave-analysis-" + scenarioSeriesName(scenario) + "-cycle-base-case";
    }

    private static String livePresetChartAlternativeFileName(ElliottScenario scenario, int alternativeIndex) {
        return "elliott-wave-analysis-" + scenarioSeriesName(scenario) + "-cycle-alternative-" + alternativeIndex;
    }

    private static String scenarioSeriesName(ElliottScenario scenario) {
        return "btc-usd";
    }

    static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
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
        int currentCycleStartIndex = latestBottomAnchorIndex(series, registry);
        BarSeriesLabelIndicator anchorLabels = new BarSeriesLabelIndicator(series, buildAnchorLabels(series, registry));
        BarSeriesLabelIndicator waveLabels = new BarSeriesLabelIndicator(series,
                buildSegmentWaveLabels(series, segmentFits));
        FixedIndicator<Num> bullishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, true, true,
                "Bullish accepted wave segments");
        FixedIndicator<Num> bearishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, false, true,
                "Bearish accepted wave segments");
        FixedIndicator<Num> bullishFallbackFits = buildScenarioFitIndicator(series, segmentFits, true, false,
                "Bullish fallback wave segments");
        FixedIndicator<Num> bearishFallbackFits = buildScenarioFitIndicator(series, segmentFits, false, false,
                "Bearish fallback wave segments");
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

    private static JFreeChart renderLiveCurrentCycleChart(BarSeries series, CurrentCycleAnalysis currentCycle) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(currentCycle, "currentCycle");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        ElliottWaveAnalysisResult.CurrentPhaseAssessment primaryFit = currentCycle.primaryFit();
        ElliottWaveAnalysisResult.CurrentPhaseAssessment alternateFit = currentCycle.alternateFit();
        BarSeriesLabelIndicator primaryLabels = primaryFit == null ? new BarSeriesLabelIndicator(series, List.of())
                : new BarSeriesLabelIndicator(series,
                        buildWaveLabelsFromScenario(series, primaryFit.scenario(), BULLISH_WAVE_COLOR));
        FixedIndicator<Num> primaryPath = primaryFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().primaryCount())
                : buildScenarioIndicator(series, primaryFit.scenario(), currentCycle.summary().primaryCount());
        FixedIndicator<Num> alternatePath = alternateFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().alternateCount())
                : buildScenarioIndicator(series, alternateFit.scenario(), currentCycle.summary().alternateCount());

        ChartPlan plan;
        if (alternateFit != null && !currentCycle.summary().alternateCount().isBlank()) {
            plan = chartWorkflow.builder()
                    .withTitle("BTC live macro current cycle")
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(alternatePath)
                    .withLineColor(BULLISH_CANDIDATE_COLOR)
                    .withLineWidth(2.0f)
                    .withOpacity(0.55f)
                    .withLabel(currentCycle.summary().alternateCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        } else {
            plan = chartWorkflow.builder()
                    .withTitle("BTC live macro current cycle")
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        }
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
        CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, selectedProfile.profile(),
                selectedProfile.historicalFitPassed() ? "historical BTC fit passed"
                        : "historical BTC fit still partial");
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
                    waveLabelForPhase(scenario.currentPhase(), index), placementForPivot(highPivot), labelColor,
                    WAVE_LABEL_FONT_SCALE));
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
        int runnerMaxScenarios = Math.max(profile.runnerMaxScenarios(), MIN_CORE_SEGMENT_SCENARIOS);
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

    private static CurrentCycleAnalysis evaluateCurrentCycle(BarSeries series, MacroLogicProfile profile,
            String historicalStatus) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(historicalStatus, "historicalStatus");

        ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        ElliottWaveAnalysisResult.CurrentCycleAssessment assessment = profileRunner.analyzeCurrentCycle(series);
        ElliottWaveAnalysisResult.CurrentPhaseAssessment primary = assessment.primary();
        ElliottWaveAnalysisResult.CurrentPhaseAssessment alternate = assessment.alternate();
        int startIndex = assessment.startIndex();
        String primaryCount = primary == null ? "No current bullish count" : primary.countLabel();
        String alternateCount = alternate == null ? "" : alternate.countLabel();
        String currentWave = primary == null ? "" : primary.currentPhase().name();
        String invalidation = primary == null ? "" : formatNum(primary.invalidationPrice());
        String startTimeUtc = series.getBar(startIndex).getEndTime().toString();
        String latestTimeUtc = series.getLastBar().getEndTime().toString();
        CurrentCycleSummary summary = new CurrentCycleSummary(startTimeUtc, latestTimeUtc, profile.id(),
                historicalStatus, primaryCount, alternateCount, currentWave, invalidation,
                primary == null ? 0.0 : primary.fitScore(), alternate == null ? 0.0 : alternate.fitScore(), "");
        return new CurrentCycleAnalysis(summary, primary, alternate, assessment.candidates());
    }

    private static Optional<SegmentScenarioFit> fitSegment(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner) {
        int startIndex = nearestIndex(series, legSegment.fromAnchor().at());
        int endIndex = nearestIndex(series, legSegment.toAnchor().at());
        if (endIndex <= startIndex) {
            return Optional.empty();
        }
        return legSegment.bullish()
                ? fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, true)
                : fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, false);
    }

    private static Optional<SegmentScenarioFit> fitSegmentFromCoreRunner(BarSeries series, LegSegment legSegment,
            MacroLogicProfile profile, ElliottWaveAnalysisRunner profileRunner, int startIndex, int endIndex,
            boolean bullish) {
        ElliottWaveAnalysisResult analysis = profileRunner.analyzeWindow(series, startIndex, endIndex);
        SegmentScenarioFit bestFallbackFit = null;
        ScenarioType expectedType = bullish ? ScenarioType.IMPULSE : null;
        ElliottPhase expectedPhase = bullish ? ElliottPhase.WAVE5 : ElliottPhase.CORRECTIVE_C;
        int expectedWaveCount = bullish ? 5 : 3;
        Boolean expectedDirection = bullish ? Boolean.TRUE : Boolean.FALSE;
        for (ElliottWaveAnalysisResult.WindowScenarioAssessment assessment : analysis.rankedBaseScenariosForWindow(
                series, startIndex, endIndex, expectedType, expectedPhase, expectedWaveCount, expectedDirection,
                MAX_CORE_ANCHOR_DRIFT_BARS)) {
            SegmentScenarioFit fit = fitFromCoreAssessment(legSegment, profile, assessment, bullish, startIndex,
                    endIndex);
            if (fit.accepted()) {
                return Optional.of(fit);
            }
            if (bestFallbackFit == null || fit.compareTo(bestFallbackFit) < 0) {
                bestFallbackFit = fit;
            }
        }
        return Optional.ofNullable(bestFallbackFit);
    }

    private static SegmentScenarioFit fitFromCoreAssessment(LegSegment legSegment, MacroLogicProfile profile,
            ElliottWaveAnalysisResult.WindowScenarioAssessment assessment, boolean bullish, int startIndex,
            int endIndex) {
        ElliottScenario scenario = assessment.scenario();
        double structureScore = assessment.structureScore();
        double ruleScore = assessment.ruleScore();
        double spacingScore = assessment.spacingScore();
        double strengthScore = assessment.strengthScore();
        double fitScore = assessment.fitScore();
        boolean accepted = assessment.passesAnchoredWindowAcceptance(startIndex, endIndex,
                Math.max(DEFAULT_ACCEPTED_SEGMENT_SCORE, profile.acceptanceThreshold()), 0.30, 0.35, 0.80,
                MAX_CORE_ANCHOR_DRIFT_BARS);
        return new SegmentScenarioFit(legSegment, scenario, fitScore, structureScore, ruleScore, spacingScore,
                strengthScore, bullish, accepted,
                bullish ? "Core-ranked anchored-window impulse fit" : "Core-ranked anchored-window corrective fit");
    }

    private static double safeConfidenceScore(double score) {
        return Double.isFinite(score) ? clamp(score, 0.0, 1.0) : 0.0;
    }

    private static double safeConfidenceScore(Num score) {
        if (score == null || score.isNaN()) {
            return 0.0;
        }
        return safeConfidenceScore(score.doubleValue());
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

    private static List<BarLabel> buildSegmentWaveLabels(BarSeries series, List<SegmentScenarioFit> segmentFits) {
        List<BarLabel> rawLabels = new ArrayList<>();
        for (SegmentScenarioFit fit : segmentFits) {
            Color labelColor = fit.accepted() ? (fit.bullish() ? BULLISH_WAVE_COLOR : BEARISH_WAVE_COLOR)
                    : (fit.bullish() ? BULLISH_CANDIDATE_COLOR : BEARISH_CANDIDATE_COLOR);
            rawLabels.addAll(buildWaveLabelsFromScenario(series, fit.scenario(), labelColor));
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
            adjusted.add(new BarLabel(label.barIndex(), adjustedY, label.text(), label.placement(), label.color(),
                    label.fontScale()));
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
        applyScenarioPath(values, series, scenario);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static FixedIndicator<Num> emptyScenarioIndicator(BarSeries series, String label) {
        Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
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
                double interpolated = interpolateOverlayPrice(fromPrice, toPrice, progress);
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

    static double interpolateOverlayPrice(double fromPrice, double toPrice, double progress) {
        double clampedProgress = clamp(progress, 0.0, 1.0);
        if (!Double.isFinite(fromPrice) || !Double.isFinite(toPrice) || fromPrice <= 0.0 || toPrice <= 0.0) {
            return fromPrice + ((toPrice - fromPrice) * clampedProgress);
        }
        double logFrom = Math.log(fromPrice);
        double logTo = Math.log(toPrice);
        return Math.exp(logFrom + ((logTo - logFrom) * clampedProgress));
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

    private static String segmentKey(LegSegment legSegment) {
        return legSegment.fromAnchor().id() + "->" + legSegment.toAnchor().id();
    }

    private static int latestBottomAnchorIndex(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return registry.anchors()
                .stream()
                .filter(anchor -> anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM)
                .max(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .map(anchor -> nearestIndex(series, anchor.at()))
                .orElse(Integer.MAX_VALUE);
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double anchorPrice(BarSeries series, int barIndex,
            ElliottWaveAnchorCalibrationHarness.AnchorType anchorType) {
        Bar bar = series.getBar(barIndex);
        return anchorType == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? bar.getHighPrice().doubleValue()
                : bar.getLowPrice().doubleValue();
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
        saveJsonSummary(report.toJson(), summaryPath, "BTC macro-cycle summary");
    }

    private static void saveSummary(LivePresetReport report, Path summaryPath) {
        saveJsonSummary(report.toJson(), summaryPath, "BTC live macro summary");
    }

    private static void saveJsonSummary(String json, Path summaryPath, String description) {
        try {
            Files.createDirectories(summaryPath.getParent());
            Files.writeString(summaryPath, json);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write " + description + " " + summaryPath, exception);
        }
    }

    private static List<MacroLogicProfile> logicProfiles() {
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

    private static MacroLogicProfile defaultLiveMacroProfile() {
        return logicProfiles().stream()
                .filter(profile -> profile.id().equals("orthodox-classical"))
                .findFirst()
                .orElseGet(() -> logicProfiles().getFirst());
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

    record LivePresetReport(String seriesName, String startTimeUtc, String latestTimeUtc, String selectedProfileId,
            String selectedHypothesisId, String chartPath, String summaryPath, CurrentCycleSummary currentCycle) {

        LivePresetReport {
            Objects.requireNonNull(seriesName, "seriesName");
            Objects.requireNonNull(startTimeUtc, "startTimeUtc");
            Objects.requireNonNull(latestTimeUtc, "latestTimeUtc");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(selectedHypothesisId, "selectedHypothesisId");
            Objects.requireNonNull(chartPath, "chartPath");
            Objects.requireNonNull(summaryPath, "summaryPath");
            Objects.requireNonNull(currentCycle, "currentCycle");
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    private record LivePresetAnalysis(MacroLogicProfile profile, CurrentCycleAnalysis currentCycle,
            LivePresetReport report) {

        LivePresetAnalysis {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(currentCycle, "currentCycle");
            Objects.requireNonNull(report, "report");
        }
    }

    private record LivePresetLegacyView(LivePresetReport report, List<LivePresetScenarioView> scenarioViews) {

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

    record CurrentCycleAnalysis(CurrentCycleSummary summary,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment primaryFit,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment alternateFit,
            List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates) {

        CurrentCycleAnalysis {
            Objects.requireNonNull(summary, "summary");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        CurrentCycleAnalysis withSummary(CurrentCycleSummary updatedSummary) {
            return new CurrentCycleAnalysis(updatedSummary, primaryFit, alternateFit, candidates);
        }
    }

    record MacroStudy(MacroProfileEvaluation selectedProfile, List<MacroProfileEvaluation> evaluations,
            List<ProfileScoreSummary> profileScores, List<DirectionalCycleSummary> cycles,
            List<HypothesisResult> hypotheses, CurrentCycleSummary currentCycle,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment currentPrimaryFit,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment currentAlternateFit) {

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
            ElliottLogicProfile coreLogicProfile, ElliottDegree runnerDegree) {

        MacroLogicProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hypothesisId, "hypothesisId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(coreLogicProfile, "coreLogicProfile");
            Objects.requireNonNull(runnerDegree, "runnerDegree");
        }

        int runnerMaxScenarios() {
            return coreLogicProfile.maxScenarios();
        }

        int runnerScenarioSwingWindow() {
            return coreLogicProfile.scenarioSwingWindow();
        }

        double acceptanceThreshold() {
            return coreLogicProfile.acceptanceThreshold();
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

}
